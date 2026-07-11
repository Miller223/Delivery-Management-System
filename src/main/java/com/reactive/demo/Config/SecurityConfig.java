package com.reactive.demo.Config; 


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // Public Endpoints (No token required)
                        .pathMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/sign-up").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/restaurants/**").permitAll()
                        .pathMatchers("/images/**").permitAll()
                        
                        // Any Authenticated User can update/view their own profile
                        .pathMatchers(HttpMethod.GET, "/api/auth/user/**").authenticated()
                        .pathMatchers(HttpMethod.PUT, "/api/auth/user/**").authenticated()
                        
                        // All other endpoints require authentication.
                        // Specific role checks are now handled by @PreAuthorize annotations in the controllers!
                        .anyExchange().authenticated()
                )
                // Tell Spring to use our custom Keycloak Role Converter below
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )
                .build();
    }

    /**
     * Custom converter to extract roles from Keycloak's unique JWT structure
     * (Extracts from realm_access -> roles and adds "ROLE_" prefix for Spring Security)
     */
    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) realmAccess.get("roles");
                
                return Flux.fromIterable(roles)
                        // Spring Security expects roles to start with "ROLE_"
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
            }
            return Flux.empty();
        });
        
        return converter;
    }
}
