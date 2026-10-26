package com.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {
    @Test void validatesSignatureAndRejectsExpiredTokens() {
        var service = new TokenService("", 3600);
        assertEquals("GV01", service.subject(service.issue("GV01")));
        assertThrows(JwtException.class, () -> new TokenService("", 3600).subject(service.issue("GV01")));
        assertThrows(JwtException.class, () -> service.subject(new TokenService("", -1).issue("GV01")));
        var expired = new TokenService("", -1);
        assertThrows(JwtException.class, () -> expired.subject(expired.issue("GV01")));
    }
}
