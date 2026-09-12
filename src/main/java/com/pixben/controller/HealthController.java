package com.pixben.controller;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private static final String VERSION = "pixben-backend-2026-09-11";

    @GetMapping({"/healthz", "/api/health"})
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("status", "UP");
        respuesta.put("service", "pixben-backend");
        respuesta.put("version", VERSION);
        respuesta.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000L);
        respuesta.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(respuesta);
    }
}
