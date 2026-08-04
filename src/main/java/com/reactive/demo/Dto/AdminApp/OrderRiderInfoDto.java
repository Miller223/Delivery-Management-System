package com.reactive.demo.Dto.AdminApp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderRiderInfoDto {
    private String riderId;
    private String name;
    private String phone;
}
