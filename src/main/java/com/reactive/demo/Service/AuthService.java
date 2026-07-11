package com.reactive.demo.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reactive.demo.Dto.*;
import com.reactive.demo.Dto.CustomerApp.UpdateUserRequestDto;
import com.reactive.demo.Dto.CustomerApp.UserInfoDto;
import com.reactive.demo.Dto.Exception.AccountNotVerifiedException;
import com.reactive.demo.Dto.Exception.AuthenticationFailedException;
import com.reactive.demo.Dto.Exception.ResourceNotFoundException;
import com.reactive.demo.Model.User;
import com.reactive.demo.Repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository; // 1. Inject the MongoDB Repository

    @Value("${KEYCLOAK_CLIENT_SECRET:zRes5IEYe6my1g8kTWraIWlnZZgeHluN}")
    private String CLIENT_SECRET;

    @Value("${KEYCLOAK_BASE_URL:http://localhost:9090}")
    private String keycloakBaseUrl;

    // 2. Add UserRepository to the constructor
    public AuthService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, UserRepository userRepository) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }
    
    //zRes5IEYe6my1g8kTWraIWlnZZgeHluN

    public Mono<LoginResponseDto> login(LoginRequestDto request) {
        return webClient.post()
                .uri(keycloakBaseUrl + "/realms/delivery-realm/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("client_id", "delivery-app")
                        .with("client_secret", CLIENT_SECRET)
                        .with("username", request.getEmail())
                        .with("password", request.getPassword())
                        .with("grant_type", "password"))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    String token = (String) response.get("access_token");
                    try {
                        String[] chunks = token.split("\\.");
                        String payload = new String(Base64.getUrlDecoder().decode(chunks[1]));
                        JsonNode jwtNode = objectMapper.readTree(payload);

                        String userRole = "CUSTOMER"; 
                        if (jwtNode.has("realm_access") && jwtNode.get("realm_access").has("roles")) {
                            for (JsonNode roleNode : jwtNode.get("realm_access").get("roles")) {
                                String r = roleNode.asText().toUpperCase();
                                if (r.equals("ADMIN") || r.equals("RIDER") || r.equals("CUSTOMER")) {
                                    userRole = r;
                                    break; 
                                }
                            }
                        }

                        return LoginResponseDto.builder()
                                .userId(jwtNode.get("sub").asText())
                                .name(jwtNode.has("given_name") ? jwtNode.get("given_name").asText() : request.getEmail())
                                .role(userRole)
                                .token(token)
                                .build();
                    } catch (Exception e) {
                        throw new RuntimeException("Error parsing identity token");
                    }
                })
                .onErrorResume(org.springframework.web.reactive.function.client.WebClientResponseException.class, ex -> {
                    String responseBody = ex.getResponseBodyAsString();
                    
                    if (responseBody != null && responseBody.contains("Account is not fully set up")) {
                        return Mono.error(new AccountNotVerifiedException("Email address has not been verified."));
                    }
                    
                    return Mono.error(new AuthenticationFailedException("Invalid email or password"));
                });
    }

    public Mono<SignupResponseDto> signUp(SignupRequestDto request) {
        return webClient.post()
                .uri(keycloakBaseUrl + "/realms/delivery-realm/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("client_id", "delivery-app")
                        .with("client_secret", CLIENT_SECRET) 
                        .with("grant_type", "client_credentials"))
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(tokenMap -> {
                    String adminToken = (String) tokenMap.get("access_token");
                    
                    Map<String, Object> keycloakUser = Map.of(
                            "username", request.getEmail(),
                            "email", request.getEmail(),
                            "firstName", request.getName(),
                            "lastName", "User",            
                            "enabled", true,
                            "requiredActions", List.of("VERIFY_EMAIL"), 
                            "credentials", List.of(
                                    Map.of("type", "password", "value", request.getPassword(), "temporary", false)
                            )
                    );

                    return webClient.post()
                            .uri(keycloakBaseUrl + "/admin/realms/delivery-realm/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(keycloakUser)
                            .exchangeToMono(clientResponse -> {
                                if (clientResponse.statusCode().equals(HttpStatus.CREATED)) {
                                    String location = clientResponse.headers().header(HttpHeaders.LOCATION).get(0);
                                    String newUserId = location.substring(location.lastIndexOf("/") + 1);
                                    
                                    String sendEmailUri = keycloakBaseUrl + "/admin/realms/delivery-realm/users/" + newUserId + "/send-verify-email?client_id=delivery-app";
                                    
                                    return webClient.put()
                                            .uri(sendEmailUri)
                                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                            .retrieve()
                                            // 1. Force the stream to emit a real object instead of Void
                                            .toBodilessEntity() 
                                            // 2. Safely chain the database operation
                                            .flatMap(response -> {
                                                System.out.println("====== KEYCLOAK SUCCESS: SAVING TO MONGO... ======");
                                                User newUser = User.builder()
                                                        .id(newUserId) 
                                                        .name(request.getName())
                                                        .email(request.getEmail())
                                                        .role("CUSTOMER")
                                                        .build();
                                                return userRepository.save(newUser);
                                            })
                                            // 3. Log the exact moment Mongo successfully writes the data
                                            .doOnSuccess(savedUser -> System.out.println("====== MONGO SAVE COMPLETE: " + savedUser.getId() + " ======"))
                                            // 4. Map the newly saved database entity to your DTO
                                            .map(savedUser -> SignupResponseDto.builder()
                                                    .userId(savedUser.getId())
                                                    .name(savedUser.getName())
                                                    .email(savedUser.getEmail())
                                                    .role(savedUser.getRole()) 
                                                    .build());
                                            
                                }
                                return clientResponse.createException().flatMap(Mono::error);
                            });
                });
    }


    public Mono<UserInfoDto> getUserInfo(String userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User profile not found in database")))
                .map(user -> UserInfoDto.builder()
                        .userId(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .image(user.getImage())
                        .phone(user.getPhone()) // Actually pulling real data from Mongo now
                        .role(user.getRole())
                        .build());
    }
    
    
    
    public Mono<UserInfoDto> updateUserProfile(String userId, UpdateUserRequestDto request) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User profile not found in database")))
                .flatMap(existingUser -> {
                    if (request.getName() != null) existingUser.setName(request.getName());
                    if (request.getImage() != null) existingUser.setImage(request.getImage());
                    if (request.getPhone() != null) existingUser.setPhone(request.getPhone());
                    
                    return userRepository.save(existingUser);
                })
                .map(savedUser -> UserInfoDto.builder()
                        .userId(savedUser.getId())
                        .name(savedUser.getName())
                        .image(savedUser.getImage())
                        .email(savedUser.getEmail())
                        .phone(savedUser.getPhone())
                        .role(savedUser.getRole())
                        .build());
    }
}