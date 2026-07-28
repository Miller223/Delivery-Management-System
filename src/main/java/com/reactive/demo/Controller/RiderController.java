package com.reactive.demo.Controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reactive.demo.Dto.RestResponse;
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
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/rider")
public class RiderController {
	
	@Autowired
	AuthService authService;
	
	@Autowired
	RiderService riderService;
	
	
	@PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/save-rider")
    public Mono<ResponseEntity<RestResponse<RiderResponseDto>>> createRider(
            @Valid @RequestBody CreateRiderRequestDto request) {
        
        return authService.createRider(request)
                .flatMap(riderInfo -> ResponseUtils.success(
                        HttpStatus.CREATED, 
                        "Rider account and vehicle created successfully", 
                        riderInfo
                ));
    }
	
	@PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Mono<ResponseEntity<RestResponse<List<RiderListResponseDto>>>> getAllRiders() {
        
        return riderService.getAllRiders()
                .collectList() // Gather the Flux stream into a single List array
                .flatMap(ridersList -> ResponseUtils.success(
                        HttpStatus.OK, 
                        "All riders fetched successfully", 
                        ridersList
                ));
    }
	
	@PreAuthorize("hasAnyRole('RIDER', 'ADMIN')")
    @PutMapping("/{riderId}/vehicle")
    public Mono<ResponseEntity<RestResponse<VehicleResponseDto>>> updateVehicle(
            @PathVariable String riderId,
            @Valid @RequestBody UpdateVehicleRequestDto request) {
        
        return riderService.updateRiderVehicle(riderId, request)
                .flatMap(updatedVehicle -> ResponseUtils.success(
                        HttpStatus.OK, 
                        "Vehicle updated successfully", 
                        updatedVehicle
                ));
    }
	
	
	@PreAuthorize("hasAnyRole('RIDER', 'ADMIN')")
    @PutMapping("/{riderId}")
    public Mono<ResponseEntity<RestResponse<UpdateRiderResponseDto>>> updateRider(
            @PathVariable String riderId,
            @RequestBody UpdateRiderRequestDto request) {
        
        return riderService.updateRiderProfile(riderId, request)
                .flatMap(response -> ResponseUtils.success(
                        HttpStatus.OK, 
                        "Rider details modified", 
                        response
                ));
    }
	
	 @PreAuthorize("hasRole('ADMIN')")
	    @DeleteMapping("/{riderId}")
	    public Mono<ResponseEntity<RestResponse<Boolean>>> deleteRider(@PathVariable String riderId) {
	        
	        return riderService.deleteRider(riderId)
	                .flatMap(success -> ResponseUtils.success(
	                        HttpStatus.OK, 
	                        "Rider removed successfully", 
	                        success
	                ));
	    }

}
