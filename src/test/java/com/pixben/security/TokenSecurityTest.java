package com.pixben.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TokenSecurityTest {
    @Test
    void hashIsStableAndDoesNotExposeRawToken() {
        String raw = "pixben-session-token";
        String hash = TokenSecurity.sha256(raw);
        assertEquals(64, hash.length());
        assertNotEquals(raw, hash);
        assertEquals(hash, TokenSecurity.sha256(raw));
    }
}
