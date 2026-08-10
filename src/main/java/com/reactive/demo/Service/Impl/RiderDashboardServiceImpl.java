package com.reactive.demo.Service.Impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;

import com.reactive.demo.Dto.CustomerApp.OrderResponseDto;
import com.reactive.demo.Dto.Exception.ResourceNotFoundException;
import com.reactive.demo.Dto.RiderApp.DashboardStatsDto;
import com.reactive.demo.Dto.RiderApp.JobSpecificationDto;
import com.reactive.demo.Dto.RiderApp.TaskCardDto;
import com.reactive.demo.Model.Order;
import com.reactive.demo.Model.Restaurant;
import com.reactive.demo.Model.User;
import com.reactive.demo.Repository.OrderRepository;
import com.reactive.demo.Repository.RestaurantRepository;
import com.reactive.demo.Repository.UserRepository;
import com.reactive.demo.Service.RiderDashboardService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RiderDashboardServiceImpl implements RiderDashboardService{
	
	@Autowired
	OrderRepository orderRepository;
	
	@Autowired 
	UserRepository userRepository;
	
	@Autowired
	RestaurantRepository restaurantRepository;
	
	@Autowired
	 private ReactiveRedisTemplate<String, String> redisTemplate;
	
	
	// --- 1. DASHBOARD STATS (Concurrent execution) ---
	@Override
    public Mono<DashboardStatsDto> getDashboardStats(String riderId) {
        Mono<Long> activeCount = orderRepository.countByRiderIdAndStatusIn(riderId, List.of("PREPARING", "OUT_FOR_DELIVERY"));
        Mono<Long> completedCount = orderRepository.countByRiderIdAndStatus(riderId, "DELIVERED");

        // Executes both DB calls at the exact same time and zips the result
        return Mono.zip(activeCount, completedCount)
                .map(tuple -> DashboardStatsDto.builder()
                        .activeCount(tuple.getT1())
                        .completedCount(tuple.getT2())
                        .build());
    }
	
	// --- 2. TASK LISTS ---
	@Override
    public Flux<TaskCardDto> getTasks(String riderId, boolean isActive) {
        Flux<Order> orderFlux = isActive 
            ? orderRepository.findByRiderIdAndStatusIn(riderId, List.of("PREPARING", "OUT_FOR_DELIVERY"))
            : orderRepository.findByRiderIdAndStatus(riderId, "DELIVERED");

        return orderFlux.flatMap(order -> 
            userRepository.findById(order.getCustomerId())
                .map(customer -> TaskCardDto.builder()
                        .orderId(order.getId())
                        .status(order.getStatus())
                        .deliveryAddress(order.getDeliveryLocation().getAddress())
                        .customerName(customer.getName())
                        .itemCount(order.getItems().stream().mapToInt(i -> i.getQuantity()).sum())
                        .totalAmount(order.getTotalAmount())
                        .build())
        );
    }
	
	// --- 3. JOB SPECIFICATION (Detailed Route View) ---
    public Mono<JobSpecificationDto> getJobDetails(String orderId) {
        return orderRepository.findById(orderId)
                .flatMap(order -> {
                    
                    Mono<User> customerMono = userRepository.findById(order.getCustomerId());
                    Mono<List<Restaurant>> restaurantsMono = restaurantRepository.findAllById(order.getRestaurantsId()).collectList();

                    return Mono.zip(customerMono, restaurantsMono)
                            .map(tuple -> {
                                User customer = tuple.getT1();
                                List<Restaurant> restaurants = tuple.getT2();

                                // 1. Map ALL restaurants into a list of individual Pickup locations!
                                List<JobSpecificationDto.PickupDto> pickupLocations = restaurants.stream()
                                        .map(restaurant -> {
                                            double lat = 0.0;
                                            double lon = 0.0;
                                            // Safely extract coordinates from GeoJSON [longitude, latitude]
                                            if (restaurant.getLocation() != null && restaurant.getLocation().getCoordinates() != null) {
                                                lon = restaurant.getLocation().getCoordinates().get(0);
                                                lat = restaurant.getLocation().getCoordinates().get(1);
                                            }
                                            
                                            return JobSpecificationDto.PickupDto.builder()
                                                    .restaurantName(restaurant.getName())
                                                    .address(restaurant.getAddress())
                                                    .latitude(lat)
                                                    .longitude(lon)
                                                    .build();
                                        })
                                        .toList();

                                // 2. Extract Shipping Phone (Fallback to customer profile phone if missing)
                                String shippingPhone = (order.getDeliveryLocation() != null && order.getDeliveryLocation().getPhone() != null)
                                        ? order.getDeliveryLocation().getPhone()
                                        : customer.getPhone();

                                // 3. Map the receipt items
                                List<JobSpecificationDto.ReceiptItemDto> receiptItems = order.getItems().stream()
                                        .map(item -> JobSpecificationDto.ReceiptItemDto.builder()
                                                .name(item.getName())
                                                .quantity(item.getQuantity())
                                                .price(item.getPriceAtPurchase() * item.getQuantity())
                                                .build())
                                        .toList();

                                return JobSpecificationDto.builder()
                                        .orderId(order.getId())
                                        .status(order.getStatus())
                                        .pickupLocations(pickupLocations) // <-- Now an Array!
                                        .deliveryDestinationAddress(order.getDeliveryLocation().getAddress())
                                        .recipient(JobSpecificationDto.RecipientDto.builder()
                                                .name(customer.getName())
                                                .phone(shippingPhone) // <-- Using Shipping Phone!
                                                .latitude(order.getDeliveryLocation().getLatitude())
                                                .longitude(order.getDeliveryLocation().getLongitude())
                                                .build())
                                        .receiptSummary(receiptItems)
                                        .totalCashCollect(order.getTotalAmount())
                                        .build();
                            });
                });
    }
    
    @Override
    public Mono<OrderResponseDto> completeDelivery(String orderId, String riderId) {
        
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found!")))
                .flatMap(order -> {
                    
                    // 1. Safety Check: Ensure it is currently out for delivery
                    if (!"OUT_FOR_DELIVERY".equalsIgnoreCase(order.getStatus())) {
                        return Mono.error(new RuntimeException("Cannot complete delivery: Order is currently " + order.getStatus()));
                    }
                    
                    // 2. Safety Check: Ensure the rider completing it is the one assigned!
                    if (order.getRiderId() == null || !order.getRiderId().equals(riderId)) {
                        return Mono.error(new RuntimeException("Unauthorized: You are not assigned to this order!"));
                    }

                    // 3. Update Order Status
                    order.setStatus("DELIVERED");

                    // 4. Update the Rider's status back to AVAILABLE
                    return userRepository.findById(riderId)
                            .flatMap(rider -> {
                                rider.setStatus("AVAILABLE");
                                return userRepository.save(rider);
                            })
                            .then(Mono.defer(() -> {
                                // 5. Add Rider back to the Redis Available Pool
                                redisTemplate.opsForZSet().add("riders:AVAILABLE", riderId, System.currentTimeMillis()).subscribe();
                                
                                // 6. Save the completed order
                                return orderRepository.save(order);
                            }));
                })
                .map(savedOrder -> OrderResponseDto.builder()
                        .orderId(savedOrder.getId())
                        .status(savedOrder.getStatus())
                        .riderId(savedOrder.getRiderId())
                        .build());
    }

}
