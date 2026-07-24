package com.reactive.demo.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reactive.demo.Dto.RestResponse;
import com.reactive.demo.Dto.AdminApp.CreateRiderRequestDto;
import com.reactive.demo.Dto.AdminApp.RiderResponseDto;
import com.reactive.demo.Service.AuthService;
import com.reactive.demo.Utils.ResponseUtils;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/rider")
public class RiderController {
	
	@Autowired
	AuthService authService;
	
	
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

}
