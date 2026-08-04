package com.reactive.demo.Service;

import com.reactive.demo.Dto.AdminOrderListDto;
import com.reactive.demo.Dto.AdminApp.AdminOrderDetailResponseDto;
import com.reactive.demo.Dto.AdminApp.RiderListResponseDto;
import com.reactive.demo.Dto.CustomerApp.OrderDetailResponseDto;
import com.reactive.demo.Dto.CustomerApp.OrderRequestDto;
import com.reactive.demo.Dto.CustomerApp.OrderResponseDto;
import com.reactive.demo.Dto.CustomerApp.UserOrderHistoryDto;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderService {
	
	Mono<OrderResponseDto> createOrder(OrderRequestDto request);

	Mono<OrderDetailResponseDto> getOrderDetails(String orderId);
	
	 Flux<UserOrderHistoryDto> getUserOrders(String userId);
	 
	 Flux<AdminOrderListDto> getAllOrders();

	Mono<RiderListResponseDto> getNearestAvailableRider(String orderId);
	
	// --- ADD THIS NEW METHOD FOR ADMIN ---
    Mono<OrderResponseDto> adminAcceptOrder(String orderId);
    
    Mono<OrderResponseDto> adminRejectOrder(String orderId);

    Mono<OrderResponseDto> assignRiderToOrder(String orderId, String riderId);

    Mono<OrderResponseDto> acceptOrder(String orderId, String riderId);
    
 // --- ADD THIS NEW ADMIN METHOD ---
    Mono<AdminOrderDetailResponseDto> getAdminOrderDetails(String orderId);
    
    
	

}
