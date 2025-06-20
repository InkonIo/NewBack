package com.example.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
    "/swagger-ui/**",
    "/v3/api-docs/**",
    "/swagger-resources/**",
    "/webjars/**",
    "/",
    "/error"
).permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(); // можно убрать, если ты не хочешь логина вообще
        return http.build();
    }
}
