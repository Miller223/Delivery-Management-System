package com.reactive.demo.Dto.CustomerApp;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserInfoDto {
    private String userId;
    private String name;
    private String image; // <-- Add this!
    private String phone;
    private String email;
    private String role;
}
