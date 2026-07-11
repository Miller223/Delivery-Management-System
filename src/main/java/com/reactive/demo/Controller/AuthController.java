package com.reactive.demo.Controller;


import com.reactive.demo.Dto.*;
import com.reactive.demo.Dto.CustomerApp.UpdateUserRequestDto;
import com.reactive.demo.Dto.CustomerApp.UserInfoDto;
import com.reactive.demo.Service.AuthService;
import com.reactive.demo.Utils.ResponseUtils;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<RestResponse<LoginResponseDto>>> login(@Valid @RequestBody LoginRequestDto request) {
        return authService.login(request)
                .flatMap(data -> ResponseUtils.success(HttpStatus.OK, "Login successful", data));
    }

    @PostMapping("/sign-up")
    public Mono<ResponseEntity<RestResponse<SignupResponseDto>>> signUp(@Valid @RequestBody SignupRequestDto request) {
        return authService.signUp(request)
                .flatMap(data -> ResponseUtils.success(HttpStatus.CREATED, "Registration successful", data));
    }
    
    @GetMapping("/user/{userId}")
    public Mono<ResponseEntity<RestResponse<UserInfoDto>>> getUserInfo(@PathVariable String userId) {
        return authService.getUserInfo(userId)
                .flatMap(userInfo -> ResponseUtils.success(
                        HttpStatus.OK, 
                        "User profile fetched successfully", 
                        userInfo
                ));
    }
    
    @PutMapping("/user/{userId}")
    public Mono<ResponseEntity<RestResponse<UserInfoDto>>> updateProfile(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRequestDto request) { 
        
        return authService.updateUserProfile(userId, request)
                .flatMap(updatedInfo -> ResponseUtils.success(
                        HttpStatus.OK, 
                        "User profile updated successfully", 
                        updatedInfo
                ));
    }
    

}
