package com.reactive.demo.Dto.RiderApp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskCardDto {
    private String orderId;
    private String status;         // e.g., "PREPARING", "OUT_FOR_DELIVERY", "DELIVERED"
    private String deliveryAddress;
    private String customerName;
    private int itemCount;
    private double totalAmount;    // For the "9,000 Ks" display
}
