package com.cms.common;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Application-wide CORS configuration, shared by every module's {@code SecurityFilterChain}
 * (each calls {@code .cors(withDefaults())}, which looks up this single bean by type) - CORS
 * is a transport-level concern common to all chains, not owned by any one identity system, so
 * it lives in {@code com.cms.common} rather than being duplicated per module.
 *
 * <p>Without this, every cross-origin request from a browser-hosted frontend (e.g. the Vite
 * dev server on a different port than the API) is blocked by the browser before a response
 * body is ever read, regardless of whether the backend itself handled the request correctly.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
