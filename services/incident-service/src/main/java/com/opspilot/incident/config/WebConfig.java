package com.opspilot.incident.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS Configuration — allows the React frontend to call our API.
 * 
 * Without this, browsers block requests from localhost:3000 (React)
 * to localhost:8081 (Spring Boot) because they're different origins.
 * 
 * CORS = Cross-Origin Resource Sharing.
 * Browsers enforce Same-Origin Policy by default for security.
 * This configuration explicitly allows our frontend origin.
 */
@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(
                                "http://localhost:3000",   // React dev server
                                "http://localhost:5173"    // Vite dev server
                        )
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600); // Cache preflight for 1 hour
            }
        };
    }
}
