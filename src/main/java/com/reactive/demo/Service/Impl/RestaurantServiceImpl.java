package com.reactive.demo.Service.Impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.reactive.demo.Dto.CustomerApp.MenusResponseDto;
import com.reactive.demo.Dto.CustomerApp.RestaurantResponseDto;
import com.reactive.demo.Dto.Exception.ResourceNotFoundException;
import com.reactive.demo.Repository.RestaurantRepository;
import com.reactive.demo.Service.ResturantService;
import com.reactive.demo.Utils.Mapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RestaurantServiceImpl implements ResturantService{
	
	@Autowired
	RestaurantRepository restaurantRepo;
	
	@Autowired
	Mapper mapper;


	@Override
	public Flux<MenusResponseDto> getAllMenuForRestaurant(String restaurantId) {
		// TODO Auto-generated method stub
		return this.restaurantRepo.findById(restaurantId)
									.switchIfEmpty(Mono.error(new ResourceNotFoundException("No restaurant found with ID: " + restaurantId)))
									.flatMapMany(restaurant-> {
										if (restaurant.getMenuItems() == null || restaurant.getMenuItems().isEmpty()) {
					                        // If restaurant exists but has no menu, just return an empty list (200 OK)
					                        return Flux.empty(); 
					                    }
										return Flux.fromIterable(restaurant.getMenuItems());
									}).map(menuItem -> {
										MenusResponseDto dto = this.mapper.map(menuItem, MenusResponseDto.class);
										return dto;
									});
	}
	



	@Override
	public Flux<RestaurantResponseDto> getAllRestaurants(int page, int size) {
		// 1. Create a pagination request (Page numbers are 0-indexed in Spring)
        Pageable pageable = PageRequest.of(page, size);

        // 2. Use findAllBy(pageable) instead of the dangerous findAll()
        return this.restaurantRepo.findAllBy(pageable)
                .map(restaurant -> {
                    RestaurantResponseDto dto = this.mapper.map(restaurant, RestaurantResponseDto.class);
                    
                    dto.setRestaurantId(restaurant.getId());
                    
                    if (restaurant.getLocation() != null && restaurant.getLocation().getCoordinates() != null) {
                        List<Double> coords = restaurant.getLocation().getCoordinates();
                        if (coords.size() >= 2) {
                            dto.setLongitude(coords.get(0)); 
                            dto.setLatitude(coords.get(1));  
                        }
                    }
                    return dto;
                });
	}
	



}
