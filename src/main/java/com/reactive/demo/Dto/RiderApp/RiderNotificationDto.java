package com.reactive.demo.Dto.RiderApp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiderNotificationDto {
    private String riderId;
    private String orderId;
    private String type;    // e.g., "NEW_ASSIGNMENT" or "ORDER_CANCELLED"
    private String message; // e.g., "You have been assigned a new delivery!"
}
