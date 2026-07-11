package com.reactive.demo.Service.Impl;




import com.reactive.demo.Dto.CustomerApp.OrderDetailItemDto;
import com.reactive.demo.Dto.CustomerApp.OrderDetailResponseDto;
import com.reactive.demo.Dto.CustomerApp.OrderItemRequestDto;
import com.reactive.demo.Dto.CustomerApp.OrderRequestDto;
import com.reactive.demo.Dto.CustomerApp.OrderResponseDto;
import com.reactive.demo.Dto.Exception.ResourceNotFoundException;
import com.reactive.demo.Model.DeliveryLocation;
import com.reactive.demo.Model.Order;
import com.reactive.demo.Model.OrderItem;
import com.reactive.demo.Model.Restaurant;
import com.reactive.demo.Repository.OrderRepository;
import com.reactive.demo.Repository.RestaurantRepository;
import com.reactive.demo.Service.OrderService;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

	@Value("${app.delivery.fee-per-km:500.0}")
	private double deliveryFeePerKm;



    @Override
    public Mono<OrderResponseDto> createOrder(OrderRequestDto request) {

        // Extract UNIQUE restaurant IDs directly from the items list
        List<String> uniqueRestaurantIds = request.getItems().stream()
                .map(item -> item.getRestaurantId())
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

                    // 2. LOOP THROUGH REQUEST ITEMS AND TRUST THE DB PRICE
                    for (OrderItemRequestDto itemDto : request.getItems()) {
                        Restaurant restaurant = restaurantMap.get(itemDto.getRestaurantId());
                        if (restaurant == null) {
                            return Mono.error(new ResourceNotFoundException("Restaurant missing in DB!"));
                        }

                        // Find the real item inside the Restaurant's DB menu items list by matching the name
                        com.reactive.demo.Model.MenuItem dbItem = restaurant.getMenuItems().stream()
                                .filter(menuItem -> menuItem.getName().equalsIgnoreCase(itemDto.getName()))
                                .findFirst()
                                .orElseThrow(() -> new ResourceNotFoundException("Fraud Alert: Menu item doesn't exist!"));

                        double truePrice = dbItem.getPrice(); // SECURE: Price from DB, not frontend!
                        itemPricesTotal += (truePrice * itemDto.getQuantity());

                        orderItems.add(OrderItem.builder()
                                .restaurantId(itemDto.getRestaurantId())
                                .name(dbItem.getName())
                                .image(itemDto.getImage())
                                .quantity(itemDto.getQuantity())
                                .priceAtPurchase(truePrice) // Save the real price
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

                    // Use the injected properties value!
                    double deliveryFee = totalDistanceKm * deliveryFeePerKm; 
                    double finalTotalAmount = itemPricesTotal + deliveryFee;

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
    public Mono<OrderDetailResponseDto> getOrderDetails(String orderId) {
        return orderRepository.findById(orderId)
                // HINT 1: Add <Order> inside Mono.error
                .switchIfEmpty(Mono.<Order>error(new ResourceNotFoundException("Order not found!")))
                // HINT 2: Explicitly declare (Order order) instead of just order
                .map((Order order) -> {
                    
                    String address = (order.getDeliveryLocation() != null) 
                            ? order.getDeliveryLocation().getAddress() : null;

                    List<OrderDetailItemDto> mappedItems = order.getItems().stream()
                            .map(item -> OrderDetailItemDto.builder()
                                    .restaurantId(item.getRestaurantId())
                                    .name(item.getName())
                                    .image(item.getImage())
                                    .quantity(item.getQuantity())
                                    .priceAtPurchase(item.getPriceAtPurchase())
                                    .build())
                            .collect(java.util.stream.Collectors.toList());

                    return OrderDetailResponseDto.builder()
                            .orderId(order.getId())
                            .restaurantIds(order.getRestaurantsId()) 
                            .status(order.getStatus())
                            .totalAmount(order.getTotalAmount())
                            .deliveryAddress(address)
                            .items(mappedItems)
                            .build();
                });
    }
    
}
