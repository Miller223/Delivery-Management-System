package com.reactive.demo.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reactive.demo.Dto.AdminApp.LocationUpdateDto;
import com.reactive.demo.Service.LocationService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
public class RiderLocationWebSocketHandler implements WebSocketHandler {

    private final LocationService locationService;
    private final ObjectMapper objectMapper;

    public RiderLocationWebSocketHandler(LocationService locationService, ObjectMapper objectMapper) {
        this.locationService = locationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        
        // session.receive() gives us a continuous stream of incoming messages from the Rider's app
        return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> {
                    try {
                        // 1. Convert the incoming JSON string into our DTO
                        LocationUpdateDto dto = objectMapper.readValue(payload, LocationUpdateDto.class);
                        
                        // Pass the status down to the service!
                        return locationService.updateRiderLocation(
                                dto.getRiderId(), 
                                dto.getLongitude(), 
                                dto.getLatitude(),
                                dto.getStatus() // <-- Added this
                        );
                    } catch (Exception e) {
                        // If the JSON is malformed, we just ignore it so the WebSocket doesn't crash
                        System.err.println("Invalid WebSocket message received: " + payload);
                        return Mono.empty(); 
                    }
                })
                .then(); // Return a Mono<Void> indicating when the session is completely done
    }
}