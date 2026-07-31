package com.reactive.demo.Config;


import com.reactive.demo.Controller.RiderLocationWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketMapping(RiderLocationWebSocketHandler locationHandler) {
        Map<String, org.springframework.web.reactive.socket.WebSocketHandler> map = new HashMap<>();
        
        // This is the URL the rider's mobile app will connect to!
        map.put("/ws/rider-location", locationHandler);

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        // Order -1 ensures Spring checks for WebSockets BEFORE regular HTTP REST APIs
        mapping.setOrder(-1); 
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
