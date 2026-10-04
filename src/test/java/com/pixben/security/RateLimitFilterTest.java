package com.pixben.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RateLimitFilterTest {
    private final RateLimitFilter filter = new RateLimitFilter(new ObjectMapper());

    @Test
    void cloudflareHeaderWinsAndForwardedForIsNotTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.addHeader("CF-Connecting-IP", "203.0.113.25");
        request.setRemoteAddr("10.0.0.2");
        assertEquals("203.0.113.25", filter.clientIp(request));
    }

    @Test
    void invalidCloudflareHeaderFallsBackToRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-Connecting-IP", "spoofed-value");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.setRemoteAddr("10.0.0.3");
        assertEquals("10.0.0.3", filter.clientIp(request));
    }
}
