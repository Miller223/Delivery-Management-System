package com.reactive.demo.Service.Impl;




import com.reactive.demo.Dto.AdminOrderListDto;
import com.reactive.demo.Dto.AdminApp.AdminOrderDetailResponseDto;
import com.reactive.demo.Dto.AdminApp.OrderCustomerInfoDto;
import com.reactive.demo.Dto.AdminApp.OrderRestaurantInfoDto;
import com.reactive.demo.Dto.AdminApp.OrderRiderInfoDto;
import com.reactive.demo.Dto.AdminApp.RiderListResponseDto;
import com.reactive.demo.Dto.AdminApp.VehicleResponseDto;
import com.reactive.demo.Dto.CustomerApp.OrderDetailItemDto;
import com.reactive.demo.Dto.CustomerApp.OrderDetailResponseDto;
import com.reactive.demo.Dto.CustomerApp.OrderItemRequestDto;
import com.reactive.demo.Dto.CustomerApp.OrderRequestDto;
import com.reactive.demo.Dto.CustomerApp.OrderRequestV2Dto;
import com.reactive.demo.Dto.CustomerApp.OrderResponseDto;
import com.reactive.demo.Dto.CustomerApp.UserOrderHistoryDto;
import com.reactive.demo.Dto.Exception.OrderProcessFailException;
import com.reactive.demo.Dto.Exception.ResourceNotFoundException;
import com.reactive.demo.Dto.RiderApp.RiderNotificationDto;
import com.reactive.demo.Model.DeliveryLocation;
import com.reactive.demo.Model.MenuItem;
import com.reactive.demo.Model.Order;
import com.reactive.demo.Model.OrderItem;
import com.reactive.demo.Model.Restaurant;
import com.reactive.demo.Repository.OrderRepository;
import com.reactive.demo.Repository.RestaurantRepository;
import com.reactive.demo.Repository.UserRepository;
import com.reactive.demo.Repository.VehicleRepository;
import com.reactive.demo.Service.NotificationService;
import com.reactive.demo.Service.OrderService;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import com.reactive.demo.Model.User;
import com.reactive.demo.Model.Vehicle;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

	@Autowired
    OrderRepository orderRepository;
	
	@Autowired
	RestaurantRepository restaurantRepository;
	
	@Autowired
    UserRepository userRepository;
	
	@Autowired
	VehicleRepository vehicleRepository;
	
	 @Autowired
	 private ReactiveRedisTemplate<String, String> redisTemplate;
	 
	 @Autowired
	 NotificationService notificationService;
	
	@Value("${app.delivery.base-fee:1500.0}")
    private double baseDeliveryFee;

    @Value("${app.delivery.fee-per-km:100.0}")
    private double deliveryFeePerKm;
    
    @Value("${app.delivery.multi-stop-fee:500.0}")
    private double multiStopFee;



    @Override
    public Mono<OrderResponseDto> createOrder(OrderRequestDto request) {

        // Extract UNIQUE restaurant IDs directly from the items list
        List<String> uniqueRestaurantIds = request.getItems().stream()
                .map(OrderItemRequestDto::getRestaurantId)
                .distinct()
                .collect(Collectors.toList());

        // Fetch all those restaurants from the database FIRST
        return restaurantRepository.findAllById(uniqueRestaurantIds)
                .collectList()
                .flatMap(restaurants -> {

                    if (restaurants.isEmpty()) {
                        return Mono.error(new ResourceNotFoundException("No valid restaurants found in database!"));
                    }

                    // Create a quick lookup map of Restaurant ID -> Restaurant Object
                    Map<String, Restaurant> restaurantMap = restaurants.stream()
                            .collect(Collectors.toMap(Restaurant::getId, r -> r));

                    double itemPricesTotal = 0.0;
                    double totalDistanceKm = 0.0;
                    List<OrderItem> orderItems = new ArrayList<>();

                    // LOOP THROUGH REQUEST ITEMS AND TRUST THE DB PRICE
                    for (OrderItemRequestDto itemDto : request.getItems()) {
                        Restaurant restaurant = restaurantMap.get(itemDto.getRestaurantId());
                        if (restaurant == null) {
                            return Mono.error(new ResourceNotFoundException("Restaurant missing in DB!"));
                        }

                        // Safety check in case the restaurant's menu array is null
                        if (restaurant.getMenuItems() == null) {
                            return Mono.error(new ResourceNotFoundException("Fraud Alert: Restaurant '" + restaurant.getName() + "' has an empty menu!"));
                        }

                        // Find the real item inside the Restaurant's DB menu items list by matching the name
                        MenuItem dbItem = restaurant.getMenuItems().stream()
                                .filter(menuItem -> menuItem.getName().equalsIgnoreCase(itemDto.getName()))
                                .findFirst()
                                // Print exactly WHICH item caused the error!
                                .orElseThrow(() -> new ResourceNotFoundException("Fraud Alert: Menu item '" + itemDto.getName() + "' doesn't exist!"));

                        double truePrice = dbItem.getPrice(); // SECURE: Price from DB, not frontend!
                        
                        // --- FIX 1: Add the item's total cost to the order total ---
                        itemPricesTotal += (truePrice * itemDto.getQuantity());

                        // --- FIX 2: Add the validated item to the order list ---
                        orderItems.add(OrderItem.builder()
                                .restaurantId(restaurant.getId())
                                .name(dbItem.getName())
                                .image(dbItem.getImage())
                                .quantity(itemDto.getQuantity())
                                .priceAtPurchase(truePrice)
                                .build());
                                
                    } // --- FIX 3: THIS CURLY BRACE WAS MISSING! ---

                    // 3. Calculate Delivery Fee based on multiple stops
                    for (Restaurant restaurant : restaurants) {
                        if (restaurant.getLocation() != null && restaurant.getLocation().getCoordinates() != null) {
                            double restLon = restaurant.getLocation().getCoordinates().get(0);
                            double restLat = restaurant.getLocation().getCoordinates().get(1);

                            totalDistanceKm += calculateDistance(
                                    request.getLatitude(), request.getLongitude(),
                                    restLat, restLon
                            );
                        }
                    }

                    // --- NEW: ADVANCED REVENUE CALCULATION ---
                    // 1. Base Fee
                    double calculatedDeliveryFee = baseDeliveryFee; 
                    
                    // 2. Add Distance Fee
                    calculatedDeliveryFee += (totalDistanceKm * deliveryFeePerKm); 
                    
                    // 3. Add Multi-Restaurant Surcharge (If order has > 1 restaurant)
                    if (uniqueRestaurantIds.size() > 1) {
                        int extraStops = uniqueRestaurantIds.size() - 1;
                        calculatedDeliveryFee += (extraStops * multiStopFee);
                    }

                    double finalTotalAmount = itemPricesTotal + calculatedDeliveryFee;

                    DeliveryLocation location = DeliveryLocation.builder()
                            .address(request.getDeliveryAddress())
                            .latitude(request.getLatitude())
                            .longitude(request.getLongitude())
                            .build();

                    Order newOrder = Order.builder()
                            .customerId(request.getCustomerId())
                            .restaurantsId(uniqueRestaurantIds)
                            .status("PENDING")
                            .totalAmount((double) Math.round(finalTotalAmount))
                            .deliveryLocation(location)
                            .items(orderItems) // Use our newly mapped safe items
                            .createdAt(LocalDateTime.now())
                            .build();

                    return orderRepository.save(newOrder);
                })
                .map(savedOrder -> OrderResponseDto.builder()
                        .orderId(savedOrder.getId())
                        .status(savedOrder.getStatus())
                        .build());
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
    
    
    @Override
    public Mono<OrderResponseDto> createOrderV2(OrderRequestV2Dto request) {

        // Extract UNIQUE restaurant IDs directly from the items list
        List<String> uniqueRestaurantIds = request.getItems().stream()
                .map(OrderItemRequestDto::getRestaurantId)
                .distinct()
                .collect(Collectors.toList());

        // Fetch all those restaurants from the database FIRST
        return restaurantRepository.findAllById(uniqueRestaurantIds)
                .collectList()
                .flatMap(restaurants -> {

                    if (restaurants.isEmpty()) {
                        return Mono.error(new ResourceNotFoundException("No valid restaurants found in database!"));
                    }

                    // Create a quick lookup map of Restaurant ID -> Restaurant Object
                    Map<String, Restaurant> restaurantMap = restaurants.stream()
                            .collect(Collectors.toMap(Restaurant::getId, r -> r));

                    double itemPricesTotal = 0.0;
                    double totalDistanceKm = 0.0;
                    List<OrderItem> orderItems = new ArrayList<>();

                    // LOOP THROUGH REQUEST ITEMS AND TRUST THE DB PRICE
                    for (OrderItemRequestDto itemDto : request.getItems()) {
                        Restaurant restaurant = restaurantMap.get(itemDto.getRestaurantId());
                        if (restaurant == null) {
                            return Mono.error(new ResourceNotFoundException("Restaurant missing in DB!"));
                        }

                        // Safety check in case the restaurant's menu array is null
                        if (restaurant.getMenuItems() == null) {
                            return Mono.error(new ResourceNotFoundException("Fraud Alert: Restaurant '" + restaurant.getName() + "' has an empty menu!"));
                        }

                       
                        MenuItem dbItem = restaurant.getMenuItems().stream()
                                .filter(menuItem -> menuItem.getId().equals(itemDto.getMenuItemId())) // <-- UPGRADED TO USE ID
                                .findFirst()
                                .orElseThrow(() -> new ResourceNotFoundException(
                                        "Fraud Alert: Menu item ID '" + itemDto.getMenuItemId() + "' doesn't exist!"
                                ));

                        double truePrice = dbItem.getPrice(); // SECURE: Price from DB, not frontend!
                        
                        // Add the item's total cost to the order total
                        itemPricesTotal += (truePrice * itemDto.getQuantity());

                        // Add the validated item to the order list
                        orderItems.add(OrderItem.builder()
                                .restaurantId(restaurant.getId())
                                .name(dbItem.getName())
                                .image(dbItem.getImage())
                                .quantity(itemDto.getQuantity())
                                .priceAtPurchase(truePrice)
                                .build());
                                
                    } 

                    // 3. Calculate Delivery Fee based on multiple stops
                    for (Restaurant restaurant : restaurants) {
                        if (restaurant.getLocation() != null && restaurant.getLocation().getCoordinates() != null) {
                            double restLon = restaurant.getLocation().getCoordinates().get(0);
                            double restLat = restaurant.getLocation().getCoordinates().get(1);

                            totalDistanceKm += calculateDistance(
                                    request.getLatitude(), request.getLongitude(),
                                    restLat, restLon
                            );
                        }
                    }

                    // --- ADVANCED REVENUE CALCULATION ---
                    // 1. Base Fee
                    double calculatedDeliveryFee = baseDeliveryFee; 
                    
                    // 2. Add Distance Fee
                    calculatedDeliveryFee += (totalDistanceKm * deliveryFeePerKm); 
                    
                    // 3. Add Multi-Restaurant Surcharge (If order has > 1 restaurant)
                    if (uniqueRestaurantIds.size() > 1) {
                        int extraStops = uniqueRestaurantIds.size() - 1;
                        calculatedDeliveryFee += (extraStops * multiStopFee);
                    }

                    double finalTotalAmount = itemPricesTotal + calculatedDeliveryFee;

                    DeliveryLocation location = DeliveryLocation.builder()
                            .address(request.getDeliveryAddress())
                            .latitude(request.getLatitude())
                            .longitude(request.getLongitude())
                            .phone(request.getShippingPhone()) // <-- INJECTED: NEW V2 FIELD
                            .build();

                    Order newOrder = Order.builder()
                            .customerId(request.getCustomerId())
                            .restaurantsId(uniqueRestaurantIds)
                            .status("PENDING")
                            .totalAmount((double) Math.round(finalTotalAmount))
                            .deliveryLocation(location)
                            .items(orderItems)
                            .createdAt(LocalDateTime.now())
                            .paymentImg(request.getPaymentImg()) // <-- INJECTED: NEW V2 FIELD
                            .build();

                    return orderRepository.save(newOrder);
                })
                .map(savedOrder -> OrderResponseDto.builder()
                        .orderId(savedOrder.getId())
                        .status(savedOrder.getStatus())
                        .build());
    }

    @Override
    public Mono<OrderDetailResponseDto> getOrderDetails(String orderId) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    
                    // Extract just the address string from the DeliveryLocation object
                    String address = (order.getDeliveryLocation() != null) 
                            ? order.getDeliveryLocation().getAddress() : null;

                    // Map the items to include the restaurantId for each item
                    List<OrderDetailItemDto> mappedItems = order.getItems().stream()
                            .map(item -> OrderDetailItemDto.builder()
                                    .restaurantId(item.getRestaurantId())
                                    .name(item.getName())
                                    .image(item.getImage())
                                    .quantity(item.getQuantity())
                                    .priceAtPurchase(item.getPriceAtPurchase())
                                    .build())
                            .collect(Collectors.toList());

                    // Build the final response DTO (without the redundant array)
                    return OrderDetailResponseDto.builder()
                            .orderId(order.getId())
                            .status(order.getStatus())
                            .totalAmount(order.getTotalAmount())
                            .deliveryAddress(address)
                            .items(mappedItems)
                            .build();
                })
                // Throws an error if the ID doesn't exist in the database
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found!")));
    }
    
    @Override
    public Flux<UserOrderHistoryDto> getUserOrders(String userId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(userId)
                // --- FIX: Change flatMap to flatMapSequential to preserve the DB sorting! ---
                .flatMapSequential(order -> {
                    // Grab the very first restaurant ID in the order
                    String firstRestaurantId = (order.getRestaurantsId() != null && !order.getRestaurantsId().isEmpty()) 
                            ? order.getRestaurantsId().get(0) : null;
                    
                    Mono<String> restaurantNameMono = Mono.just("Unknown Restaurant");
                    
                    if (firstRestaurantId != null) {
                        restaurantNameMono = restaurantRepository.findById(firstRestaurantId)
                                .map(restaurant -> {
                                    String name = restaurant.getName();
                                    // Append "+ X more" if the order has items from multiple restaurants
                                    if (order.getRestaurantsId().size() > 1) {
                                        name += " + " + (order.getRestaurantsId().size() - 1) + " more";
                                    }
                                    return name;
                                })
                                .defaultIfEmpty("Unknown Restaurant");
                    }

                    // Build the DTO using the fetched name
                    return restaurantNameMono.map(name -> UserOrderHistoryDto.builder()
                            .orderId(order.getId())
                            .restaurantName(name)
                            .totalAmount(order.getTotalAmount())
                            .status(order.getStatus())
                            .createdAt(order.getCreatedAt())
                            .build());
                });
    }
    
    @Override
    public Flux<AdminOrderListDto> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc()
                // Use flatMapSequential to ensure the newest orders stay at the top of the list!
                .flatMapSequential(order -> 
                    
                    // Fetch the user using the customerId
                    userRepository.findById(order.getCustomerId())
                            .map(user -> user.getName()) // Extract just the name
                            .defaultIfEmpty("Unknown Customer") // Safe fallback
                            
                            // Build the final DTO with the retrieved name
                            .map(customerName -> AdminOrderListDto.builder()
                                    .orderId(order.getId())
                                    .totalAmount(order.getTotalAmount())
                                    .status(order.getStatus())
                                    .customerName(customerName) // Inject the name here
                                    .build())
                );
    }
    
    
    @Override
    public Mono<RiderListResponseDto> getNearestAvailableRider(String orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found!")))
                .flatMap(order -> {
                    // 1. Get the first restaurant in the order to use as the pickup point
                    String restaurantId = (order.getRestaurantsId() != null && !order.getRestaurantsId().isEmpty()) 
                            ? order.getRestaurantsId().get(0) : null;
                            
                    if (restaurantId == null) {
                        return Mono.error(new ResourceNotFoundException("Order has no restaurants attached"));
                    }
                    
                    return restaurantRepository.findById(restaurantId);
                })
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Restaurant not found!")))
                .flatMap(restaurant -> {
                    // 2. Ensure the restaurant actually has GPS coordinates saved
                    if (restaurant.getLocation() == null || restaurant.getLocation().getCoordinates() == null) {
                        return Mono.error(new RuntimeException("Restaurant location is unknown"));
                    }
                    
                    // 3. Extract Longitude and Latitude
                    double lon = restaurant.getLocation().getCoordinates().get(0);
                    double lat = restaurant.getLocation().getCoordinates().get(1);
                    
                    // 4. Query REDIS for the nearest riders within a 10-Kilometer radius
                    Point restaurantPoint = new Point(lon, lat);
                    Distance searchRadius = new Distance(10.0, Metrics.KILOMETERS);
                    
                    // 4.5 Use Spring Boot 3 'search' method - Passing Distance directly!
                 // 4.5 Use Spring Boot 3 'search' method
                    return redisTemplate.opsForGeo().search(
                            "riders:AVAILABLE", 
                            GeoReference.fromCoordinate(restaurantPoint), 
                            searchRadius,
                            RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().sortAscending()
                        )
                        // 1. Process riders in order of closeness
                        .concatMap(geoResult -> {
                            String riderId = geoResult.getContent().getName();
                            return userRepository.findById(riderId);
                        })
                        // 2. THE SHIELD: Ignore them if Mongo says they are actually BUSY
                        .filter(user -> "AVAILABLE".equals(user.getStatus())) 
                        // 3. NOW grab the closest one who passed the test
                        .next() 
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("No actually available riders found within 10km!")))
                        .map(user -> RiderListResponseDto.builder()
                                .riderId(user.getId())
                                .name(user.getName())
                                .phone(user.getPhone())
                                .status(user.getStatus())
                                .build());
                });
    }
    
    
 /// --- 1. NEW METHOD: Admin accepts the order from the customer ---
    @Override
    public Mono<OrderResponseDto> adminAcceptOrder(String orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found!")))
                .flatMap(order -> {
                    
                    // --- ADD THIS SAFETY CHECK ---
                    if (!"PENDING".equalsIgnoreCase(order.getStatus())) {
                        return Mono.error(new RuntimeException("Order cannot be accepted because it is already in status: " + order.getStatus()));
                    }

                    // Change status so the restaurant starts cooking
                    order.setStatus("PREPARING");
                    return orderRepository.save(order);
                })
                .map(savedOrder -> OrderResponseDto.builder()
                        .orderId(savedOrder.getId())
                        .status(savedOrder.getStatus())
                        .riderId(savedOrder.getRiderId())
                        .build());
    }

    // --- 2. UPDATED: Admin assigns the rider (WITH STATE VALIDATION) ---
    @Override
    public Mono<OrderResponseDto> assignRiderToOrder(String orderId, String riderId) {
        
        // 1. FIRST find the order and validate its state to protect the Rider!
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found!")))
                .flatMap(order -> {
                    
                    // --- ADD THIS SAFETY CHECK ---
                    if (!"PREPARING".equalsIgnoreCase(order.getStatus())) {
                        return Mono.error(new RuntimeException("Cannot assign rider: Order is currently " + order.getStatus() + " (Must be PREPARING)"));
                    }

                    // 2. Order is safe. Now find and update the Rider.
                    return userRepository.findById(riderId)
                            .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rider not found!")))
                            .flatMap(rider -> {
                                rider.setStatus("BUSY");
                                return userRepository.save(rider);
                            })
                            .then(Mono.defer(() -> {
                                // 3. Update Order with Rider's ID and OUT_FOR_DELIVERY status
                                order.setRiderId(riderId);
                                order.setStatus("OUT_FOR_DELIVERY");
                                
                                // 4. FIX: Chain the Redis deletion into the reactive stream!
                                // Use opsForGeo() to match your search method, and chain it with .then()
                                return redisTemplate.opsForGeo().remove("riders:AVAILABLE", riderId)
                                        .then(orderRepository.save(order));
                            }))
                            // --- ADD THIS BLOCK RIGHT HERE ---
                            .doOnSuccess(savedOrder -> {
                                // 5. FIRE REAL-TIME NOTIFICATION TO THE RIDER!
                                notificationService.sendNotification(RiderNotificationDto.builder()
                                        .riderId(riderId)
                                        .orderId(savedOrder.getId())
                                        .type("NEW_TASK")
                                        .message("You have a new active delivery task to complete!")
                                        .build());
                            });
                })
                .map(savedOrder -> OrderResponseDto.builder()
                        .orderId(savedOrder.getId())
                        .status(savedOrder.getStatus())
                        .riderId(savedOrder.getRiderId()) 
                        .build());
    }

    // --- 3. UPDATED: Rider acknowledges the assignment ---
    @Override
    public Mono<OrderResponseDto> acceptOrder(String orderId, String riderId) {
        
        // The admin already did the heavy lifting! We just verify the rider owns this order.
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found!")))
                .flatMap(order -> {
                    
                    // Verify that the Admin actually assigned THIS specific rider
                    if (!riderId.equals(order.getRiderId())) {
                        return Mono.error(new RuntimeException("Error: You are not assigned to this order!"));
                    }

                    // Order and Rider statuses REMAIN "OUT_FOR_DELIVERY" and "BUSY".
                    // We just return the order to the Rider app as a successful acknowledgment!
                    return Mono.just(order);
                })
                .map(savedOrder -> OrderResponseDto.builder()
                        .orderId(savedOrder.getId())
                        .status(savedOrder.getStatus())
                        .riderId(savedOrder.getRiderId())
                        .build());
    }
    
    
    @Override
    public Mono<AdminOrderDetailResponseDto> getAdminOrderDetails(String orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found!")))
                .flatMap(order -> {
                    // 1. Fetch Customer
                    Mono<User> customerMono = userRepository.findById(order.getCustomerId())
                            .defaultIfEmpty(new User()); 

                    // 2. Fetch Rider
                    Mono<User> riderMono = order.getRiderId() != null 
                            ? userRepository.findById(order.getRiderId())
                            : Mono.empty();

                    // --- NEW 3. Fetch Rider's Vehicle ---
                    Mono<Vehicle> vehicleMono = order.getRiderId() != null
                            ? vehicleRepository.findByRiderId(order.getRiderId())
                            : Mono.empty();

                    // 4. Fetch Restaurants
                    Mono<List<OrderRestaurantInfoDto>> restaurantsMono = order.getRestaurantsId() != null && !order.getRestaurantsId().isEmpty()
                            ? restaurantRepository.findAllById(order.getRestaurantsId())
                                .map(r -> {
                                    Double rLat = null;
                                    Double rLng = null;
                                    if (r.getLocation() != null && r.getLocation().getCoordinates() != null && r.getLocation().getCoordinates().size() >= 2) {
                                        rLng = r.getLocation().getCoordinates().get(0); 
                                        rLat = r.getLocation().getCoordinates().get(1); 
                                    }
                                    return OrderRestaurantInfoDto.builder()
                                            .restaurantId(r.getId())
                                            .name(r.getName())
                                            .phone(r.getPhone())
                                            .image(r.getImage())
                                            .address(r.getAddress())
                                            .latitude(rLat)
                                            .longitude(rLng)
                                            .build();
                                }).collectList()
                            : Mono.just(new java.util.ArrayList<>());

                    // 5. Zip 5 things together concurrently (Tuple5)
                    return Mono.zip(
                                Mono.just(order), 
                                customerMono, 
                                riderMono.defaultIfEmpty(new User()), 
                                restaurantsMono,
                                vehicleMono.defaultIfEmpty(new Vehicle()) // Add Vehicle to the Zip!
                            )
                            .map(tuple -> {
                                Order o = tuple.getT1();
                                User customer = tuple.getT2();
                                User rider = tuple.getT3();
                                List<OrderRestaurantInfoDto> mappedRestaurants = tuple.getT4();
                                Vehicle vehicle = tuple.getT5();

                                // Map Customer Info
                                OrderCustomerInfoDto customerInfo = OrderCustomerInfoDto.builder()
                                        .userId(customer.getId())
                                        .name(customer.getName())
                                        .phone(o.getDeliveryLocation().getPhone() != null ? o.getDeliveryLocation().getPhone() : customer.getPhone())
                                        .latitude(o.getDeliveryLocation() != null ? o.getDeliveryLocation().getLatitude() : null)
                                        .longitude(o.getDeliveryLocation() != null ? o.getDeliveryLocation().getLongitude() : null)
                                        .build();

                                // Safe Rider GPS
                                Double riderLat = null;
                                Double riderLng = null;
                                if (rider.getCurrentLocation() != null && rider.getCurrentLocation().getCoordinates() != null && rider.getCurrentLocation().getCoordinates().size() >= 2) {
                                    riderLng = rider.getCurrentLocation().getCoordinates().get(0);
                                    riderLat = rider.getCurrentLocation().getCoordinates().get(1);
                                }

                                // --- NEW: Map Vehicle Info safely ---
                                VehicleResponseDto mappedVehicle = vehicle.getId() != null ? VehicleResponseDto.builder()
                                        .id(vehicle.getId())
                                        .riderId(vehicle.getRiderId())
                                        .type(vehicle.getType())
                                        .licenceNumber(vehicle.getLicenceNumber())
                                        .createdAt(vehicle.getCreatedAt())
                                        .build() : null;

                                // Map Rider Info (now including Vehicle)
                                OrderRiderInfoDto riderInfo = rider.getId() != null ? OrderRiderInfoDto.builder()
                                        .riderId(rider.getId())
                                        .name(rider.getName())
                                        .phone(rider.getPhone())
                                        .email(rider.getEmail())
                                        .image(rider.getImage())
                                        .status(rider.getStatus())
                                        .nrcNumber(rider.getNrcNumber())
                                        .latitude(riderLat)
                                        .longitude(riderLng)
                                        .vehicle(mappedVehicle) // <-- ATTACHED HERE!
                                        .build() : null;

                                // Map Food Items
                                List<OrderDetailItemDto> mappedItems = o.getItems().stream()
                                        .map(item -> OrderDetailItemDto.builder()
                                                .restaurantId(item.getRestaurantId())
                                                .name(item.getName())
                                                .image(item.getImage())
                                                .quantity(item.getQuantity())
                                                .priceAtPurchase(item.getPriceAtPurchase())
                                                .build())
                                        .collect(Collectors.toList());

                                // Build Final Admin Response
                                return AdminOrderDetailResponseDto.builder()
                                        .orderId(o.getId())
                                        .status(o.getStatus())
                                        .totalAmount(o.getTotalAmount())
                                        .deliveryAddress(o.getDeliveryLocation() != null ? o.getDeliveryLocation().getAddress() : null)
                                        
                                        // --- NEW: INJECT SHIPPING PHONE AND PAYMENT IMAGE ---
                                        .shippingPhone(o.getDeliveryLocation() != null ? o.getDeliveryLocation().getPhone() : customer.getPhone())
                                        .paymentImg(o.getPaymentImg())
                                        
                                        .createdAt(o.getCreatedAt())
                                        .customer(customerInfo)
                                        .rider(riderInfo)
                                        .restaurants(mappedRestaurants) 
                                        .items(mappedItems)
                                        .build();
                            });
                });
    }

                  
    
    
    @Override
    public Mono<OrderResponseDto> adminRejectOrder(String orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found!")))
                .flatMap(order -> {
                    
                    // Safety check: Cannot reject if already finished
                    if ("DELIVERED".equalsIgnoreCase(order.getStatus()) || "CANCELLED".equalsIgnoreCase(order.getStatus())) {
                        return Mono.error(new OrderProcessFailException("Order cannot be rejected because it is already: " + order.getStatus()));
                    }

                    // Change status
                    order.setStatus("CANCELLED");

                    // If a rider was already assigned, we must free them up!
                    if (order.getRiderId() != null) {
                        return userRepository.findById(order.getRiderId())
                                .flatMap(rider -> {
                                    rider.setStatus("AVAILABLE");
                                    return userRepository.save(rider);
                                })
                                .then(orderRepository.save(order));
                    }

                    // If no rider was assigned yet, just save the cancelled order
                    return orderRepository.save(order);
                })
                .map(savedOrder -> OrderResponseDto.builder()
                        .orderId(savedOrder.getId())
                        .status(savedOrder.getStatus())
                        .riderId(savedOrder.getRiderId())
                        .build());
    }

    
}
