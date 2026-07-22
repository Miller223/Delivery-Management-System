package com.reactive.demo.Controller;


import com.reactive.demo.Dto.RestResponse;
import com.reactive.demo.Dto.CustomerApp.OrderDetailResponseDto;
import com.reactive.demo.Dto.CustomerApp.OrderRequestDto;
import com.reactive.demo.Dto.CustomerApp.OrderResponseDto;
import com.reactive.demo.Dto.CustomerApp.UserOrderHistoryDto;
import com.reactive.demo.Service.OrderService;
import com.reactive.demo.Utils.ResponseUtils;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    OrderService orderService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/save-order")
    public Mono<ResponseEntity<RestResponse<OrderResponseDto>>> saveOrder(@RequestBody OrderRequestDto request) {
        return this.orderService.createOrder(request)
                .flatMap(response -> ResponseUtils.success(
                        HttpStatus.CREATED, 
                        "Order placed successfully",
                        response
                ));
    }
    
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/getOrderDetails/{orderId}")
    public Mono<ResponseEntity<RestResponse<OrderDetailResponseDto>>> getOrderDetails(@PathVariable String orderId) {
        return orderService.getOrderDetails(orderId)
                .flatMap(response -> ResponseUtils.success(
                        HttpStatus.OK, 
                        "Order details fetched",
                        response
                ));
    }
    
    @GetMapping("/getUserOrders/{userId}")
    public Mono<ResponseEntity<RestResponse<List<UserOrderHistoryDto>>>> getUserOrders(@PathVariable String userId) {
        return orderService.getUserOrders(userId)
                .collectList()
                .flatMap(orders -> ResponseUtils.success(
                        HttpStatus.OK, 
                        "User orders history retrieved",
                        orders
                ));
    }
}
