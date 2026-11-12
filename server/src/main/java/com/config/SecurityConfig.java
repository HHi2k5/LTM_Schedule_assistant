package com.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import com.security.TokenFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    @org.springframework.beans.factory.annotation.Value("${app.cors.origins:http://localhost:5173,http://127.0.0.1:5173}")
    private List<String> allowedOrigins;
    private final TokenFilter tokenFilter;
    private final ObjectMapper mapper;

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<TokenFilter> tokenFilterRegistration() {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(tokenFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    mapper.writeValue(response.getWriter(), Map.of("status", "ERROR", "message", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."));
                })
                .accessDeniedHandler((request, response, error) -> {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    mapper.writeValue(response.getWriter(), Map.of("status", "ERROR", "message", "Bạn không có quyền thực hiện thao tác này."));
                }))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/schedule/auth/login", "/api/schedule/auth/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/static/**", "/*.ico", "/*.json").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/schedule/auto-generate-excel", "/api/schedule/auto-generate-3files").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/schedule/department-schedule").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/schedule/auth/change-password").authenticated()
                // Khung giờ tiết học: mọi tài khoản đăng nhập xem được; chỉ admin được chỉnh sửa.
                .requestMatchers(HttpMethod.GET, "/api/schedule/period-templates").authenticated()
                .requestMatchers("/api/schedule/period-templates", "/api/schedule/period-templates/**").hasRole("ADMIN")
                // Danh mục: admin quản lý chung; trưởng bộ môn xem bộ môn/môn học và quản lý chuyên môn giảng viên trong bộ môn mình.
                .requestMatchers(HttpMethod.GET, "/api/schedule/departments", "/api/schedule/subjects",
                        "/api/schedule/teacher-subjects").hasAnyRole("ADMIN", "DEPT_HEAD")
                .requestMatchers("/api/schedule/teacher-subjects", "/api/schedule/teacher-subjects/**").hasAnyRole("ADMIN", "DEPT_HEAD")
                .requestMatchers("/api/schedule/rooms", "/api/schedule/rooms/**", "/api/schedule/room-types", "/api/schedule/room-types/**",
                        "/api/schedule/departments", "/api/schedule/departments/**", "/api/schedule/subjects", "/api/schedule/subjects/**").hasRole("ADMIN")
                // Lịch bận giảng viên thỉnh giảng: trưởng bộ môn chỉ xem; chỉ giảng viên tự khai báo/xóa.
                .requestMatchers(HttpMethod.GET, "/api/schedule/assignments/visiting/**").hasAnyRole("DEPT_HEAD", "TEACHER")
                .requestMatchers("/api/schedule/assignments/visiting/**").hasRole("TEACHER")
                // Xếp lịch / phân lớp đã mở cho giảng viên thỉnh giảng: chỉ trưởng bộ môn thực hiện.
                .requestMatchers(HttpMethod.POST, "/api/schedule/assignments/auto-visiting",
                        "/api/schedule/visiting-schedule").hasRole("DEPT_HEAD")
                // Bàn giao lịch từ Admin -> Trưởng bộ môn (dispatch/reopen chỉ Admin; submit chỉ Trưởng bộ môn).
                .requestMatchers(HttpMethod.POST, "/api/schedule/assignments/dispatch", "/api/schedule/assignments/reopen").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/schedule/assignments/submit").hasRole("DEPT_HEAD")
                // Phân công giảng viên: chỉ trưởng bộ môn thực hiện (admin chỉ đọc tổng quan lịch đã xếp).
                .requestMatchers(HttpMethod.GET, "/api/schedule/assignments").hasAnyRole("ADMIN", "DEPT_HEAD")
                .requestMatchers("/api/schedule/assignments", "/api/schedule/assignments/**").hasRole("DEPT_HEAD")
                .requestMatchers(HttpMethod.POST, "/api/schedule/upload-excel-teacher").hasRole("TEACHER")
                .requestMatchers(HttpMethod.DELETE, "/api/schedule/school").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/schedule/personal-courses").hasRole("TEACHER")
                .requestMatchers(HttpMethod.POST, "/api/schedule/personal-courses/delete").hasRole("TEACHER")
                .requestMatchers(HttpMethod.GET, "/api/schedule/week/*", "/api/schedule/auth/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/schedule/events").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers("/error").permitAll()
                .anyRequest().denyAll()
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
