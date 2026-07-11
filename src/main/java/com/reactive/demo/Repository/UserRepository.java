package com.reactive.demo.Repository;


import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.reactive.demo.Model.User;


@Repository
public interface UserRepository extends ReactiveMongoRepository<User, String> {
	
}
