package com.pixben.config;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final List<String> allowedOriginPatterns;

    public WebConfig(@Value("${app.cors.allowed-origin-patterns:https://pixben.netlify.app,https://*.netlify.app,http://localhost:*,http://127.0.0.1:*}") String origins) {
        this.allowedOriginPatterns = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(valor -> !valor.isBlank())
                .distinct()
                .toList();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String ruta = Paths.get("imagen").toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/imagen/**")
                .addResourceLocations("file:" + ruta + "/");
    }

    /**
     * CORS centralizado. Para añadir un dominio propio configura en Render:
     * CORS_ALLOWED_ORIGIN_PATTERNS=https://pixben.netlify.app,https://tudominio.com
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOriginPatterns(allowedOriginPatterns);
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-Session-Token", "Accept", "Origin", "X-Requested-With"));
        configuracion.setExposedHeaders(List.of("Location", "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset"));
        configuracion.setAllowCredentials(false);
        configuracion.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", configuracion);
        return new CorsFilter(fuente);
    }
}
