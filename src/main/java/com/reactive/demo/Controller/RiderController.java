package com.reactive.demo.Controller;


import com.reactive.demo.Dto.*;
import com.reactive.demo.Dto.AdminApp.CreateRiderRequestDto;
import com.reactive.demo.Dto.AdminApp.RiderListResponseDto;
import com.reactive.demo.Dto.AdminApp.RiderResponseDto;
import com.reactive.demo.Dto.AdminApp.UpdateRiderRequestDto;
import com.reactive.demo.Dto.AdminApp.UpdateRiderResponseDto;
import com.reactive.demo.Dto.AdminApp.UpdateVehicleRequestDto;
import com.reactive.demo.Dto.AdminApp.VehicleResponseDto;
import com.reactive.demo.Service.AuthService;
import com.reactive.demo.Service.RiderService;
import com.reactive.demo.Utils.ResponseUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/rider")
public class RiderController {

    private final RiderService riderService;
    private final AuthService authService; // Needed for Keycloak Creation

    public RiderController(RiderService riderService, AuthService authService) {
        this.riderService = riderService;
        this.authService = authService;
    }

    // 1. ADMIN ONLY: Create a New Rider
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Mono<ResponseEntity<RestResponse<com.reactive.demo.Dto.AdminApp.RiderResponseDto>>> createRider(
            @Valid @RequestBody CreateRiderRequestDto request) {
        
        // We use authService because creating a rider requires creating a Keycloak account!
        return authService.createRider(request)
                .flatMap(response -> ResponseUtils.success(
                        HttpStatus.CREATED, 
                        "Rider registered successfully", 
                        response
                ));
    }

    // 2. ADMIN ONLY: Get All Riders List
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Mono<ResponseEntity<RestResponse<List<RiderListResponseDto>>>> getAllRiders() {
        return riderService.getAllRiders()
                .collectList()
                .flatMap(list -> ResponseUtils.success(
                        HttpStatus.OK, 
                        "Riders fetched successfully", 
                        list
                ));
    }

    // 3. ADMIN & RIDER: Get Single Rider Profile
    @PreAuthorize("hasAnyRole('ADMIN', 'RIDER')")
    @GetMapping("/{riderId}")
    public Mono<ResponseEntity<RestResponse<RiderResponseDto>>> getRiderById(@PathVariable String riderId) {
        return riderService.getRiderById(riderId)
                .flatMap(response -> ResponseUtils.success(
                        HttpStatus.OK, 
                        "Rider details fetched successfully", 
                        response
                ));
    }

    // 4. ADMIN & RIDER: Update Profile
    @PreAuthorize("hasAnyRole('ADMIN', 'RIDER')")
    @PutMapping("/{riderId}")
    public Mono<ResponseEntity<RestResponse<UpdateRiderResponseDto>>> updateRiderProfile(
            @PathVariable String riderId,
            @Valid @RequestBody UpdateRiderRequestDto request) {
        
        return riderService.updateRiderProfile(riderId, request)
                .flatMap(response -> ResponseUtils.success(
                        HttpStatus.OK, 
                        "Rider profile updated", 
                        response
                ));
    }

    // 5. ADMIN & RIDER: Update Vehicle
    @PreAuthorize("hasAnyRole('ADMIN', 'RIDER')")
    @PutMapping("/{riderId}/vehicle")
    public Mono<ResponseEntity<RestResponse<VehicleResponseDto>>> updateRiderVehicle(
            @PathVariable String riderId,
            @Valid @RequestBody UpdateVehicleRequestDto request) {
        
        return riderService.updateRiderVehicle(riderId, request)
                .flatMap(response -> ResponseUtils.success(
                        HttpStatus.OK, 
                        "Vehicle details updated", 
                        response
                ));
    }

    // 6. ADMIN ONLY: Delete Rider
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{riderId}")
    public Mono<ResponseEntity<RestResponse<Boolean>>> deleteRider(@PathVariable String riderId) {
        
        return riderService.deleteRider(riderId)
                .flatMap(success -> ResponseUtils.success(
                        HttpStatus.OK, 
                        "Rider removed completely", 
                        success
                ));
    }
}
