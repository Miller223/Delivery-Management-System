package com.reactive.demo.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
public class RedisConfig {

    @Bean
    @Primary // <-- 2. ADD THIS ANNOTATION
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        // We use String serializers because we will just be storing Rider IDs as Strings in the Geo index!
        return new ReactiveRedisTemplate<>(factory, RedisSerializationContext.string());
    }
}
