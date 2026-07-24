package com.reactive.demo.Service;

import com.reactive.demo.Dto.AdminApp.UpdateVehicleRequestDto;
import com.reactive.demo.Dto.AdminApp.VehicleResponseDto;

import reactor.core.publisher.Mono;

public interface RiderService {
	Mono<VehicleResponseDto> updateRiderVehicle(String riderId,UpdateVehicleRequestDto request);

}
