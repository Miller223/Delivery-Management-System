package com.reactive.demo.Dto.AdminApp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

import com.reactive.demo.Dto.CustomerApp.OrderDetailItemDto;

@Data
@Builder
public class AdminOrderDetailResponseDto {
    private String orderId;
    private String status;
    private Double totalAmount;
    private String deliveryAddress;
    private LocalDateTime createdAt;
    
    // Nested objects to give the Admin full context
    private OrderCustomerInfoDto customer;
    private OrderRiderInfoDto rider;
    private List<OrderDetailItemDto> items;
}
