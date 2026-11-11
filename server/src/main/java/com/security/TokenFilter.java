package com.security;

import com.repository.TeacherRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TokenFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    private final TeacherRepository teachers;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                teachers.findById(tokens.subject(header.substring(7))).filter(t -> t.getUsername() != null)
                        .filter(t -> "TEACHER".equals(t.getRole()) || "ADMIN".equals(t.getRole()) || "DEPT_HEAD".equals(t.getRole()))
                        .ifPresent(t -> {
                            var principal = new AccountPrincipal(t.getTeacherId(), t.getUsername(), t.getRole(), t.getDepartmentId());
                            // Trưởng bộ môn vẫn là giảng viên: giữ toàn bộ quyền của TEACHER và thêm quyền phân công.
                            var authorities = "DEPT_HEAD".equals(t.getRole())
                                    ? List.of(new SimpleGrantedAuthority("ROLE_DEPT_HEAD"), new SimpleGrantedAuthority("ROLE_TEACHER"))
                                    : List.of(new SimpleGrantedAuthority("ROLE_" + t.getRole()));
                            var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
