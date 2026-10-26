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
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/schedule/auth/login", "/api/schedule/auth/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/static/**", "/*.ico", "/*.json").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/schedule/auto-generate-excel", "/api/schedule/auto-generate-3files").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/schedule/rooms").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/schedule/upload-excel-teacher").hasRole("TEACHER")
                .requestMatchers(HttpMethod.DELETE, "/api/schedule/school").hasRole("TEACHER")
                .requestMatchers(HttpMethod.GET, "/api/schedule/week/*", "/api/schedule/auth/me").authenticated()
                .requestMatchers("/error").permitAll()
                .anyRequest().denyAll()
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Cho phép tất cả các nguồn (bao gồm cả các domain ngrok từ bên ngoài và localhost)
        configuration.setAllowedOriginPatterns(List.of("*")); 
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true); // Cho phép gửi cookie/credential

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
