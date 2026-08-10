package com.reactive.demo.Repository;

import java.util.Collection;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.reactive.demo.Model.Order;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderRepository extends ReactiveMongoRepository<Order, String>{
	
    Flux<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    Flux<Order> findAllByOrderByCreatedAtDesc();
    
 // For the Active Tasks tab (Finding PREPARING or OUT_FOR_DELIVERY)
    Flux<Order> findByRiderIdAndStatusIn(String riderId, Collection<String> statuses);
    
    // For the Completed Tasks tab
    Flux<Order> findByRiderIdAndStatus(String riderId, String status);

    // For the Stats Widget (Optimized count queries)
    Mono<Long> countByRiderIdAndStatusIn(String riderId, Collection<String> statuses);
    Mono<Long> countByRiderIdAndStatus(String riderId, String status);
	

}
