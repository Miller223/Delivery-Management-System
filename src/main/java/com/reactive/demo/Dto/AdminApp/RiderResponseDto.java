package com.reactive.demo.Dto.AdminApp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiderResponseDto {
    private String userId;
    private String name;
    private String image;
    private String phone;
    private String email;
    private String role;
    
    // Nested vehicle information!
    private VehicleResponseDto vehicle;
}
