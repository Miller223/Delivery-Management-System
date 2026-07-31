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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reactive.demo.Dto.RestResponse;
import com.reactive.demo.Dto.AdminApp.CreateRestaurantRequestDto;
import com.reactive.demo.Dto.AdminApp.UpdateRestaurantRequestDto;
import com.reactive.demo.Dto.CustomerApp.MenusResponseDto;
import com.reactive.demo.Dto.CustomerApp.RestaurantResponseDto;
import com.reactive.demo.Service.ResturantService;
import com.reactive.demo.Utils.ResponseUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/restaurants")
public class ResturantController {
	
	@Autowired
	ResturantService restaurantService;
	
	@GetMapping
    public Mono<ResponseEntity<RestResponse<List<RestaurantResponseDto>>>> getAllRestaurants(
            // 2. Add @Min and @Max to protect your database!
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size 
    ) {
        return restaurantService.getAllRestaurants(page, size)
                .collectList()
                .flatMap(restaurantsList -> ResponseUtils.success(
                        HttpStatus.OK, 
                        "Restaurants fetched successfully", 
                        restaurantsList
                ));
    }
	
	@GetMapping("/{restaurantId}")
	public Mono<ResponseEntity<RestResponse<List<MenusResponseDto>>>> getMenuOfSingleRestaurant(@PathVariable String restaurantId){
		return this.restaurantService.getAllMenuForRestaurant(restaurantId)
										.collectList()
										.flatMap(menuItems ->{
											return ResponseUtils.success(HttpStatus.OK, "Menu Items fetched successfully", menuItems);
										});
		
	}
	
	
	@GetMapping("/getRestaurantByID/{id}")
    public Mono<ResponseEntity<RestResponse<RestaurantResponseDto>>> getRestaurantById(@PathVariable String id) {
        return restaurantService.getRestaurantById(id)
                .flatMap(restaurant -> ResponseUtils.success(
                        HttpStatus.OK, 
                        "Restaurant fetched successfully", 
                        restaurant
                ));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Mono<ResponseEntity<RestResponse<RestaurantResponseDto>>> createRestaurant(
            @Valid @RequestBody CreateRestaurantRequestDto request) {
        
        return restaurantService.createRestaurant(request)
                .flatMap(restaurant -> ResponseUtils.success(
                        HttpStatus.CREATED, 
                        "Restaurant created successfully", 
                        restaurant
                ));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Mono<ResponseEntity<RestResponse<RestaurantResponseDto>>> updateRestaurant(
            @PathVariable String id,
            @RequestBody UpdateRestaurantRequestDto request) {
        
        return restaurantService.updateRestaurant(id, request)
                .flatMap(restaurant -> ResponseUtils.success(
                        HttpStatus.OK, 
                        "Restaurant updated successfully", 
                        restaurant
                ));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<RestResponse<Boolean>>> deleteRestaurant(@PathVariable String id) {
        
        return restaurantService.deleteRestaurant(id)
                .flatMap(success -> ResponseUtils.success(
                        HttpStatus.OK, 
                        "Restaurant deleted successfully", 
                        success
                ));
    }
	
	
	

}
