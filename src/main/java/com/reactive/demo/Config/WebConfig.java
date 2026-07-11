package com.reactive.demo.Config;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebFluxConfigurer {

    // 1. Static Resource Handler for Image Uploads
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get("uploads");
        String uploadPath = uploadDir.toFile().getAbsolutePath();
        
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }

    // 2. Global CORS Configuration for WebFlux
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Apply CORS to all endpoints in the app
                .allowedOrigins(
                    "http://localhost:3000", // Standard React local port
                    "http://localhost:5173"  // Standard Vite + React local port (just in case your friend uses Vite)
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Allow all CRUD operations
                .allowedHeaders("*") // Allow all headers (Authorization, Content-Type, etc.)
                .allowCredentials(true) // Allow cookies or auth headers if needed later
                .maxAge(3600); // Cache the CORS preflight response for 1 hour to increase speed
    }
    
    
}
