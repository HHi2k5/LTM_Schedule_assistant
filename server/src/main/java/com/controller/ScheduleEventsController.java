package com.controller;

import com.security.AccountPrincipal;
import com.security.TokenService;
import com.service.ScheduleEventBroker;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class ScheduleEventsController {
    private final ScheduleEventBroker events;
    private final TokenService tokens;

    @GetMapping(value = "/api/schedule/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(@AuthenticationPrincipal AccountPrincipal account,
                                              @RequestHeader("Authorization") String authorization) {
        if (!authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new BadCredentialsException("Phiên đăng nhập không hợp lệ.");
        }
        Instant expiresAt;
        try { expiresAt = tokens.expiresAt(authorization.substring(7)); }
        catch (JwtException | IllegalArgumentException error) {
            throw new BadCredentialsException("Phiên đăng nhập không hợp lệ hoặc đã hết hạn.");
        }
        var emitter = events.subscribe(account, expiresAt);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no").body(emitter);
    }
}
