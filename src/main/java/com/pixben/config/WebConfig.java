package com.pixben.config;

import java.nio.file.Paths;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String ruta = Paths.get("imagen").toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/imagen/**")
                .addResourceLocations("file:" + ruta + "/");
    }

    /**
     * Aplica CORS incluso a respuestas de error, preflight y rutas REST.
     * Mientras el proyecto está en desarrollo se permiten todos los orígenes.
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOriginPatterns(List.of("*"));
        configuracion.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        configuracion.setAllowedHeaders(List.of("*"));
        configuracion.setExposedHeaders(List.of("Location"));
        configuracion.setAllowCredentials(false);
        configuracion.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", configuracion);
        return new CorsFilter(fuente);
    }
}
