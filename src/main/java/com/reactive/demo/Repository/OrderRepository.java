package com.reactive.demo.Repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.reactive.demo.Model.Order;

import reactor.core.publisher.Flux;

public interface OrderRepository extends ReactiveMongoRepository<Order, String>{
	
	Flux<Order> findByCustomerId(String customerId);
	

}
