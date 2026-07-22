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
    private final UserRepository userRepository;

    @Value("${KEYCLOAK_CLIENT_SECRET}") 
    private String CLIENT_SECRET;

    @Value("${KEYCLOAK_BASE_URL:http://localhost:9090}")
    private String keycloakBaseUrl;

    // 2. Add UserRepository to the constructor
    public AuthService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, UserRepository userRepository) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }
    
   

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
                .flatMap(response -> {
                    String token = (String) response.get("access_token");
                    try {
                        String[] chunks = token.split("\\.");
                        String payload = new String(Base64.getUrlDecoder().decode(chunks[1]));
                        JsonNode jwtNode = objectMapper.readTree(payload);

                        String userRole = "CUSTOMER"; 
                        if (jwtNode.has("realm_access") && jwtNode.get("realm_access").has("roles")) {
                            boolean isAdmin = false;
                            boolean isRider = false;
                            
                            for (JsonNode roleNode : jwtNode.get("realm_access").get("roles")) {
                                String r = roleNode.asText().toUpperCase();
                                if (r.equals("ADMIN")) isAdmin = true;
                                if (r.equals("RIDER")) isRider = true;
                            }
                            
                            if (isAdmin) {
                                userRole = "ADMIN";
                            } else if (isRider) {
                                userRole = "RIDER";
                            }
                        }

                        String userId = jwtNode.get("sub").asText();
                        
                        // CHANGED: Grab the full "name" from the JWT
                        String name = request.getEmail();
                        if (jwtNode.has("name")) {
                            name = jwtNode.get("name").asText();
                        } else if (jwtNode.has("given_name")) {
                            name = jwtNode.get("given_name").asText();
                            if (jwtNode.has("family_name")) {
                                name += " " + jwtNode.get("family_name").asText();
                            }
                        }
                        
                        final String finalUserRole = userRole;
                        final String finalName = name;

                        LoginResponseDto loginResponse = LoginResponseDto.builder()
                                .userId(userId)
                                .name(finalName)
                                .role(finalUserRole)
                                .token(token)
                                .build();

                        return userRepository.existsById(userId)
                                .flatMap(exists -> {
                                    if (!exists) {
                                        User syncUser = User.builder()
                                                .id(userId)
                                                .name(finalName)
                                                .email(request.getEmail())
                                                .role(finalUserRole)
                                                .build();
                                        return userRepository.save(syncUser).thenReturn(loginResponse);
                                    }
                                    return Mono.just(loginResponse);
                                });

                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Error parsing identity token"));
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
                    
                    // Email Verification is RESTORED here
                    Map<String, Object> keycloakUser = Map.of(
                            "username", request.getEmail(),
                            "email", request.getEmail(),
                            "firstName", request.getName(),
                            "lastName", "User",
                            "enabled", true,
                            "requiredActions", List.of("VERIFY_EMAIL"), 
                            "credentials", List.of(Map.of("type", "password", "value", request.getPassword(), "temporary", false))
                    );

                    return webClient.post()
                            .uri(keycloakBaseUrl + "/admin/realms/delivery-realm/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(keycloakUser)
                            .retrieve()
                            .onStatus(status -> status.value() == 409, 
                                      response -> Mono.error(new AuthenticationFailedException("Email address is already in use.")))
                            .toBodilessEntity()
                            .flatMap(clientResponse -> {
                                String location = clientResponse.getHeaders().getLocation().getPath();
                                String newUserId = location.substring(location.lastIndexOf("/") + 1);
                                
                                // Email Sending call is RESTORED here
                                return webClient.put()
                                        .uri(keycloakBaseUrl + "/admin/realms/delivery-realm/users/" + newUserId + "/send-verify-email?client_id=delivery-app")
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                        .retrieve()
                                        .toBodilessEntity()
                                        .flatMap(r -> userRepository.save(User.builder()
                                                .id(newUserId)
                                                .name(request.getName())
                                                .email(request.getEmail())
                                                .role("CUSTOMER")
                                                .build()))
                                        .map(savedUser -> SignupResponseDto.builder()
                                                .userId(savedUser.getId())
                                                .name(savedUser.getName())
                                                .email(savedUser.getEmail())
                                                .role(savedUser.getRole())
                                                .build());
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
                        .phone(user.getPhone())
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