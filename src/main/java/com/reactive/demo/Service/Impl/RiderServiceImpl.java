package com.reactive.demo.Service.Impl;

import org.springframework.beans.factory.annotation.Autowired;

import com.reactive.demo.Dto.AdminApp.UpdateVehicleRequestDto;
import com.reactive.demo.Dto.AdminApp.VehicleResponseDto;
import com.reactive.demo.Repository.UserRepository;
import com.reactive.demo.Repository.VehicleRepository;
import com.reactive.demo.Service.RiderService;
import com.reactive.demo.Utils.Mapper;

import reactor.core.publisher.Mono;

public class RiderServiceImpl implements RiderService{
	
	@Autowired
	UserRepository userRepository;
	
	@Autowired
	VehicleRepository vehicleRepository;
	
	@Autowired
	Mapper mapper;

	@Override
	public Mono<VehicleResponseDto> updateRiderVehicle(String riderId, UpdateVehicleRequestDto request) {
		// TODO Auto-generated method stub
		return null;
	}

}
