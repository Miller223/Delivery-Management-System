package com.reactive.demo.Service;

import com.reactive.demo.Dto.CustomerApp.MenusResponseDto;
import com.reactive.demo.Dto.CustomerApp.RestaurantResponseDto;

import reactor.core.publisher.Flux;

public interface ResturantService {
	
	Flux<RestaurantResponseDto> getAllRestaurants(int page, int size);
	Flux<MenusResponseDto> getAllMenuForRestaurant(String restaurantId);

}
