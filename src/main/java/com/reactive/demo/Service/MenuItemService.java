package com.reactive.demo.Service;

import com.reactive.demo.Dto.AdminApp.CreateMenuItemRequestDto;
import com.reactive.demo.Dto.AdminApp.UpdateMenuItemRequestDto;
import com.reactive.demo.Dto.CustomerApp.MenuItemResponseDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MenuItemService {
    Flux<MenuItemResponseDto> getMenuByRestaurantId(String restaurantId);
    
    Mono<MenuItemResponseDto> createMenuItem(String restaurantId, CreateMenuItemRequestDto request);
    
    Mono<MenuItemResponseDto> updateMenuItem(String restaurantId, String menuItemId, UpdateMenuItemRequestDto request);
    
    Mono<Boolean> deleteMenuItem(String restaurantId, String menuItemId);
}
