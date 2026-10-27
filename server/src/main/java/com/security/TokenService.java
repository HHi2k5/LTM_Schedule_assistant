package com.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.util.Date;

@Service
public class TokenService {
    private final Key key;
    private final long lifetimeSeconds;

    @org.springframework.beans.factory.annotation.Autowired
    public TokenService(@Value("${app.jwt.secret:}") String secret,
                        @Value("${app.jwt.lifetime-seconds:28800}") long lifetimeSeconds,
                        org.springframework.core.env.Environment environment) {
        this(secret, lifetimeSeconds);
        if (secret.isBlank() && environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))) {
            throw new IllegalArgumentException("Prod requires a configured JWT secret.");
        }
    }

    public TokenService(String secret, long lifetimeSeconds) {
        // Without a configured key, local sessions intentionally expire on server restart.
        this.key = secret.isBlank() ? Keys.secretKeyFor(SignatureAlgorithm.HS256)
                : Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.lifetimeSeconds = lifetimeSeconds;
    }

    public String issue(String teacherId) {
        Instant now = Instant.now();
        return Jwts.builder().setSubject(teacherId).setIssuer("schedule-assistant")
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(lifetimeSeconds)))
                .signWith(key).compact();
    }

    public String subject(String token) {
        return Jwts.parserBuilder().setSigningKey(key).requireIssuer("schedule-assistant")
                .build().parseClaimsJws(token).getBody().getSubject();
    }
}
