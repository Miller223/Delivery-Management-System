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

// ─── NEW IMPORTS FOR EXPLICIT CORS ───
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {
    
	 @Bean
	    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
	        return http
	                // Tell Spring Security to use the corsConfigurationSource bean below
	                .cors(Customizer.withDefaults()) 
	                .csrf(ServerHttpSecurity.CsrfSpec::disable)
	                .authorizeExchange(exchanges -> exchanges
	                        // Public Endpoints
	                        .pathMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
	                        .pathMatchers(HttpMethod.POST, "/api/auth/sign-up").permitAll()
	                        .pathMatchers(HttpMethod.GET, "/api/restaurants/**").permitAll()
	                        .pathMatchers("/images/**").permitAll()
	                     // --- FIX: Make the WebSocket Endpoint completely public! ---
	                        .pathMatchers("/ws/**").permitAll()
	                        
	                        // Authenticated Endpoints
	                        .pathMatchers(HttpMethod.GET, "/api/auth/user/**").authenticated()
	                        .pathMatchers(HttpMethod.PUT, "/api/auth/user/**").authenticated()
	                        .anyExchange().authenticated()
	                )
	                .oauth2ResourceServer(oauth2 -> oauth2
	                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
	                )
	                .build();
	    }

    // ─── THE BULLETPROOF CORS CONFIGURATION BEAN ───
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Explicitly allow your frontend URLs
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        
        // Apply this configuration to all routes
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }

    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) realmAccess.get("roles");
                
                return Flux.fromIterable(roles)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
            }
            return Flux.empty();
        });
        
        return converter;
    }
}