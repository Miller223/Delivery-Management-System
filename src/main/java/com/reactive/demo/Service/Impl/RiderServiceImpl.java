package com.reactive.demo.Service.Impl;


import com.reactive.demo.Dto.AdminApp.RiderListResponseDto;
import com.reactive.demo.Dto.AdminApp.RiderResponseDto;
import com.reactive.demo.Dto.AdminApp.UpdateRiderRequestDto;
import com.reactive.demo.Dto.AdminApp.UpdateRiderResponseDto;
import com.reactive.demo.Dto.AdminApp.UpdateVehicleRequestDto;
import com.reactive.demo.Dto.AdminApp.VehicleResponseDto;
import com.reactive.demo.Dto.Exception.ResourceNotFoundException;
import com.reactive.demo.Repository.UserRepository;
import com.reactive.demo.Repository.VehicleRepository;
import com.reactive.demo.Service.AuthService;
import com.reactive.demo.Service.RiderService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RiderServiceImpl implements RiderService {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final AuthService authService; 

    public RiderServiceImpl(UserRepository userRepository, VehicleRepository vehicleRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.authService = authService;
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

    // --- ADDED: GET RIDER BY ID (With Vehicle Data) ---
    @Override
    public Mono<RiderResponseDto> getRiderById(String riderId) {
        return userRepository.findById(riderId)
                // Ensure the user is actually a rider!
                .filter(user -> "RIDER".equals(user.getRole()))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rider not found!")))
                .flatMap(user -> 
                    // Fetch their vehicle to embed in the response
                    vehicleRepository.findByRiderId(riderId)
                        .map(vehicle -> VehicleResponseDto.builder()
                                .id(vehicle.getId())
                                .riderId(vehicle.getRiderId())
                                .type(vehicle.getType())
                                .licenceNumber(vehicle.getLicenceNumber())
                                .createdAt(vehicle.getCreatedAt())
                                .build()
                        )
                        .map(vehicleDto -> mapToRiderResponse(user, vehicleDto))
                        // If they don't have a vehicle yet, still return the rider profile safely
                        .defaultIfEmpty(mapToRiderResponse(user, null))
                );
    }

    @Override
    public Mono<UpdateRiderResponseDto> updateRiderProfile(String riderId, UpdateRiderRequestDto request) {
        return userRepository.findById(riderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rider not found in database.")))
                .flatMap(existingRider -> {
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
                        .phone(savedRider.getPhone()) // <-- map it to the response!
                        .email(savedRider.getEmail())
                        .nrcNumber(savedRider.getNrcNumber()) 
                        .build());
    }
    
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
    public Mono<Boolean> deleteRider(String riderId) {
        return userRepository.findById(riderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rider not found in database.")))
                .flatMap(user -> {
                    Mono<Void> deleteUser = userRepository.delete(user);
                    Mono<Void> deleteVehicle = vehicleRepository.deleteByRiderId(riderId);
                    Mono<Void> deleteKeycloak = authService.deleteUserInKeycloak(riderId);

                    return Mono.when(deleteUser, deleteVehicle, deleteKeycloak).thenReturn(true);
                });
    }

    // Helper Method
    private RiderResponseDto mapToRiderResponse(com.reactive.demo.Model.User user, VehicleResponseDto vehicle) {
        return RiderResponseDto.builder()
                .userId(user.getId())
                .name(user.getName())
                .image(user.getImage())
                .phone(user.getPhone())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .nrcNumber(user.getNrcNumber())
                .vehicle(vehicle)
                .build();
    }
}
