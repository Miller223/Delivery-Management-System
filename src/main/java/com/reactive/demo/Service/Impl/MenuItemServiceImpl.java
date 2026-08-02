package com.reactive.demo.Service.Impl;

import com.reactive.demo.Dto.AdminApp.CreateMenuItemRequestDto;
import com.reactive.demo.Dto.AdminApp.UpdateMenuItemRequestDto;
import com.reactive.demo.Dto.CustomerApp.MenuItemResponseDto;
import com.reactive.demo.Dto.Exception.ResourceNotFoundException;
import com.reactive.demo.Model.MenuItem;
import com.reactive.demo.Repository.RestaurantRepository;
import com.reactive.demo.Service.MenuItemService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.UUID;

@Service
public class MenuItemServiceImpl implements MenuItemService {

    private final RestaurantRepository restaurantRepository;

    public MenuItemServiceImpl(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    @Override
    public Flux<MenuItemResponseDto> getMenuByRestaurantId(String restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Restaurant not found!")))
                // FlatMapIterable extracts the embedded list and turns it into a Flux stream
                .flatMapIterable(restaurant -> {
                    if (restaurant.getMenuItems() == null) {
                        return new ArrayList<MenuItem>();
                    }
                    return restaurant.getMenuItems();
                })
                .map(item -> mapToDto(item, restaurantId));
    }

    @Override
    public Mono<MenuItemResponseDto> createMenuItem(String restaurantId, CreateMenuItemRequestDto request) {
        return restaurantRepository.findById(restaurantId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cannot add menu item: Restaurant not found!")))
                .flatMap(restaurant -> {
                    
                    // Initialize the array if this is the very first item!
                    if (restaurant.getMenuItems() == null) {
                        restaurant.setMenuItems(new ArrayList<>());
                    }

                    MenuItem item = MenuItem.builder()
                            // --- FIX 1: MANUALLY GENERATE THE ID HERE ---
                            .id(UUID.randomUUID().toString()) 
                            .name(request.getName())
                            .description(request.getDescription())
                            .image(request.getImage())
                            .category(request.getCategory())
                            .price(request.getPrice())
                            .isAvailable(request.getIsAvailable())
                            .build();

                    // Add it to the embedded array
                    restaurant.getMenuItems().add(item);

                    // Save the entire restaurant document
                    return restaurantRepository.save(restaurant).thenReturn(item);
                })
                .map(item -> mapToDto(item, restaurantId));
    }

    @Override
    public Mono<MenuItemResponseDto> updateMenuItem(String restaurantId, String menuItemId, UpdateMenuItemRequestDto request) {
        return restaurantRepository.findById(restaurantId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Restaurant not found!")))
                .flatMap(restaurant -> {
                    
                    if (restaurant.getMenuItems() == null) {
                        return Mono.error(new ResourceNotFoundException("Menu item not found!"));
                    }

                    // Find the specific item in the embedded array
                    MenuItem existingItem = restaurant.getMenuItems().stream()
                            // --- FIX 2: ADD NULL SAFETY CHECK ---
                            .filter(item -> item.getId() != null && item.getId().equals(menuItemId))
                            .findFirst()
                            .orElse(null);

                    if (existingItem == null) {
                        return Mono.error(new ResourceNotFoundException("Menu item not found!"));
                    }

                    // Update fields if provided
                    if (request.getName() != null) existingItem.setName(request.getName());
                    if (request.getDescription() != null) existingItem.setDescription(request.getDescription());
                    if (request.getImage() != null) existingItem.setImage(request.getImage());
                    if (request.getCategory() != null) existingItem.setCategory(request.getCategory());
                    if (request.getPrice() != null) existingItem.setPrice(request.getPrice());
                    if (request.getIsAvailable() != null) existingItem.setIsAvailable(request.getIsAvailable());

                    // Saving the restaurant inherently saves the changes to the embedded item
                    return restaurantRepository.save(restaurant).thenReturn(existingItem);
                })
                .map(item -> mapToDto(item, restaurantId));
    }

    @Override
    public Mono<Boolean> deleteMenuItem(String restaurantId, String menuItemId) {
        return restaurantRepository.findById(restaurantId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Restaurant not found!")))
                .flatMap(restaurant -> {
                    
                    if (restaurant.getMenuItems() == null) {
                        return Mono.error(new ResourceNotFoundException("Menu item not found!"));
                    }

                    // removeIf scans the array and deletes the matching item natively
                    // --- FIX 3: ADD NULL SAFETY CHECK ---
                    boolean removed = restaurant.getMenuItems().removeIf(item -> item.getId() != null && item.getId().equals(menuItemId));

                    if (!removed) {
                        return Mono.error(new ResourceNotFoundException("Menu item not found!"));
                    }

                    return restaurantRepository.save(restaurant).thenReturn(true);
                });
    }

    private MenuItemResponseDto mapToDto(MenuItem item, String restaurantId) {
        return MenuItemResponseDto.builder()
                .id(item.getId())
                .restaurantId(restaurantId) 
                .name(item.getName())
                .description(item.getDescription())
                .image(item.getImage())
                .category(item.getCategory())
                .price(item.getPrice())
                .isAvailable(item.getIsAvailable())
                .build();
    }
}
