package com.pixben.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Límite ligero por IP para endpoints públicos que suelen recibir abuso.
 * Protege la lógica de la aplicación; no reemplaza la protección DDoS del proveedor/CDN.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_BUCKETS = 20_000;
    private final ConcurrentHashMap<String, Window> buckets = new ConcurrentHashMap<>();
    private final AtomicInteger operations = new AtomicInteger();
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        return policyFor(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Policy policy = policyFor(request);
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        cleanupOccasionally();
        long now = System.currentTimeMillis();
        String key = policy.name + ":" + clientIp(request);
        Window window = buckets.compute(key, (ignored, current) -> {
            if (current == null || now >= current.expiresAt) return new Window(1, now + policy.windowMillis);
            current.count += 1;
            return current;
        });

        long retryAfter = Math.max(1L, (window.expiresAt - now + 999L) / 1000L);
        response.setHeader("X-RateLimit-Limit", String.valueOf(policy.limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, policy.limit - window.count)));
        response.setHeader("X-RateLimit-Reset", String.valueOf(window.expiresAt / 1000L));

        if (window.count > policy.limit) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "timestamp", Instant.now(),
                    "status", 429,
                    "message", "Demasiadas solicitudes. Espera un momento e inténtalo nuevamente."
            ));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Policy policyFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return null;
        String path = request.getRequestURI();
        if (path.equals("/usuarios/login")) return new Policy("login", 10, 10 * 60_000L);
        if (path.equals("/usuarios")) return new Policy("registro", 8, 60 * 60_000L);
        if (path.equals("/contactos")) return new Policy("contacto", 15, 60 * 60_000L);
        if (path.equals("/pedidos/invitado")) return new Policy("pedido-invitado", 20, 60 * 60_000L);
        if (path.equals("/pedidos/invitado/consultar")) return new Policy("consulta-invitado", 30, 10 * 60_000L);
        if (path.equals("/pedidos")) return new Policy("pedido", 30, 60 * 60_000L);
        if (path.equals("/resenas")) return new Policy("resena", 30, 60 * 60_000L);
        if (path.equals("/pedidos-personalizados")) return new Policy("personalizado", 10, 60 * 60_000L);
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String cf = request.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) return cf.trim();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",", 2)[0].trim();
            if (!first.isBlank()) return first;
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private void cleanupOccasionally() {
        int count = operations.incrementAndGet();
        if (count % 500 != 0 && buckets.size() < MAX_BUCKETS) return;
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt);
        if (buckets.size() > MAX_BUCKETS) buckets.clear();
    }

    private record Policy(String name, int limit, long windowMillis) {}

    private static final class Window {
        private int count;
        private final long expiresAt;
        private Window(int count, long expiresAt) { this.count = count; this.expiresAt = expiresAt; }
    }
}
