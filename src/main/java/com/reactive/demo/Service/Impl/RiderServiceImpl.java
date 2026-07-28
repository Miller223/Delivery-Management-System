package com.reactive.demo.Service.Impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.reactive.demo.Dto.AdminApp.RiderListResponseDto;
import com.reactive.demo.Dto.AdminApp.UpdateRiderRequestDto;
import com.reactive.demo.Dto.AdminApp.UpdateRiderResponseDto;
import com.reactive.demo.Dto.AdminApp.UpdateVehicleRequestDto;
import com.reactive.demo.Dto.AdminApp.VehicleResponseDto;
import com.reactive.demo.Dto.Exception.ResourceNotFoundException;
import com.reactive.demo.Repository.UserRepository;
import com.reactive.demo.Repository.VehicleRepository;
import com.reactive.demo.Service.AuthService;
import com.reactive.demo.Service.RiderService;
import com.reactive.demo.Utils.Mapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RiderServiceImpl implements RiderService{
	
	@Autowired
	UserRepository userRepository;
	
	@Autowired
	VehicleRepository vehicleRepository;
	
	@Autowired
	AuthService authService;
	
	@Autowired
	Mapper mapper;

	@Override
    public Mono<VehicleResponseDto> updateRiderVehicle(String riderId, UpdateVehicleRequestDto request) {
        return vehicleRepository.findByRiderId(riderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("No vehicle found for this rider.")))
                .flatMap(existingVehicle -> {
                    existingVehicle.setType(request.getType());
                    existingVehicle.setLicenceNumber(request.getLicenceNumber());
                    
                    return vehicleRepository.save(existingVehicle);
                })
                .map(savedVehicle -> VehicleResponseDto.builder()
                        .id(savedVehicle.getId())
                        .riderId(savedVehicle.getRiderId())
                        .type(savedVehicle.getType())
                        .licenceNumber(savedVehicle.getLicenceNumber())
                        .createdAt(savedVehicle.getCreatedAt())
                        .build());
    }

	@Override
    public Flux<RiderListResponseDto> getAllRiders() {
       
        return userRepository.findByRole("RIDER")
                .map(user -> RiderListResponseDto.builder()
                        .riderId(user.getId())
                        .name(user.getName())
                        .phone(user.getPhone())
                        .status(user.getStatus() != null ? user.getStatus() : "AVAILABLE") 
                        .build()
                );
    }
	
	
	@Override
    public Mono<UpdateRiderResponseDto> updateRiderProfile(String riderId, UpdateRiderRequestDto request) {
        return userRepository.findById(riderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rider not found in database.")))
                .flatMap(existingRider -> {
                    // Update only the fields that were provided in the JSON request
                    if (request.getName() != null) existingRider.setName(request.getName());
                    if (request.getImage() != null) existingRider.setImage(request.getImage());
                    if (request.getPhone() != null) existingRider.setPhone(request.getPhone());
                    if (request.getNrcNumber() != null) existingRider.setNrcNumber(request.getNrcNumber());
                    
                    return userRepository.save(existingRider);
                })
                .map(savedRider -> UpdateRiderResponseDto.builder()
                        .riderId(savedRider.getId())
                        .image(savedRider.getImage())
                        .name(savedRider.getName())
                        .nrcNumber(savedRider.getNrcNumber()) // Map it back to the response here!
                        .build());
    }

	@Override
    public Mono<Boolean> deleteRider(String riderId) {
        return userRepository.findById(riderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rider not found in database.")))
                .flatMap(user -> {
                    // 1. Delete the user document from MongoDB
                    Mono<Void> deleteUser = userRepository.delete(user);
                    
                    // 2. Delete their associated vehicle document from MongoDB
                    Mono<Void> deleteVehicle = vehicleRepository.deleteByRiderId(riderId);

                    // 3. Delete the user completely from Keycloak!
                    Mono<Void> deleteKeycloak = authService.deleteUserInKeycloak(riderId);

                    // 4. Execute all three delete operations at the exact same time
                    return Mono.when(deleteUser, deleteVehicle, deleteKeycloak).thenReturn(true);
                });
    }

}
