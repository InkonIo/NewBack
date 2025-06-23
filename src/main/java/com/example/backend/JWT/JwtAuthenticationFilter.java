package com.example.backend.JWT;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.backend.repository.UserRepository;
import com.example.backend.entiity.User; 

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Добавьте этот импорт для логирования

@Component
@RequiredArgsConstructor
@Slf4j // Аннотация Lombok для автоматического создания логгера
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        log.info("JWT Filter: Request URL: {}", request.getRequestURI()); // Логгируем URL запроса

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("JWT Filter: No Bearer token found or Authorization header missing. Proceeding without authentication.");
            filterChain.doFilter(request, response);
            return;
        }

        final String jwtToken = authHeader.substring(7);
        log.info("JWT Filter: Extracted token: {}", jwtToken);

        final String userEmail = jwtService.extractEmail(jwtToken);
        log.info("JWT Filter: Extracted email from token: {}", userEmail);

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            log.info("JWT Filter: User email is not null and no existing authentication. Looking up user.");
            
            userRepository.findByEmail(userEmail).ifPresentOrElse(user -> { 
                log.info("JWT Filter: User found in DB: {}", user.getEmail());
                if (jwtService.isTokenValid(jwtToken, user.getEmail())) {
                    log.info("JWT Filter: Token is valid for user: {}", user.getEmail());
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            user, 
                            null, 
                            user.getAuthorities() 
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.info("JWT Filter: SecurityContextHolder updated for user: {}", user.getEmail());
                } else {
                    log.warn("JWT Filter: Token is NOT valid for user: {}", user.getEmail());
                }
            }, () -> {
                log.warn("JWT Filter: User with email {} NOT found in database.", userEmail);
            });
        } else if (userEmail == null) {
            log.warn("JWT Filter: User email extracted from token was NULL. Token might be malformed or invalid.");
        } else { // SecurityContextHolder.getContext().getAuthentication() != null
            log.info("JWT Filter: User {} already authenticated. Skipping.", userEmail);
        }

        filterChain.doFilter(request, response);
    }
}
