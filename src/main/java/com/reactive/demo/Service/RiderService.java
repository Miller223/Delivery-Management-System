package com.reactive.demo.Service;

import com.reactive.demo.Dto.AdminApp.RiderListResponseDto;
import com.reactive.demo.Dto.AdminApp.UpdateRiderRequestDto;
import com.reactive.demo.Dto.AdminApp.UpdateRiderResponseDto;
import com.reactive.demo.Dto.AdminApp.UpdateVehicleRequestDto;
import com.reactive.demo.Dto.AdminApp.VehicleResponseDto;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RiderService {
	Mono<VehicleResponseDto> updateRiderVehicle(String riderId,UpdateVehicleRequestDto request);
	Flux<RiderListResponseDto> getAllRiders();
	Mono<UpdateRiderResponseDto> updateRiderProfile(String riderId, UpdateRiderRequestDto request);
	Mono<Boolean> deleteRider(String riderId);

}
