package com.pixben.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private static final String VERSION = "pixben-backend-2026-07-14";

    @GetMapping("/healthz")
    public Map<String, Object> health() {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("status", "ok");
        respuesta.put("version", VERSION);
        respuesta.put("timestamp", Instant.now().toString());
        return respuesta;
    }
}
