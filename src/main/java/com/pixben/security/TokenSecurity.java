package com.pixben.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class TokenSecurity {
    private TokenSecurity() {}

    public static String sha256(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Token vacío");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no disponible", error);
        }
    }
}
