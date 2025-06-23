package com.example.backend.JWT;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.backend.repository.UserRepository;
import com.example.backend.entiity.User; // Убедитесь, что импортируете свой класс User

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwtToken = authHeader.substring(7); // Удаляем "Bearer "
        final String userEmail = jwtService.extractEmail(jwtToken); // Лучше явно назвать метод

        // Проверяем, что email пользователя не null и что пользователь еще не аутентифицирован
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            userRepository.findByEmail(userEmail).ifPresent(user -> { // Получаем наш объект User
                // Валидируем токен, используя email пользователя из БД
                if (jwtService.isTokenValid(jwtToken, user.getEmail())) {
                    // --- ВАЖНОЕ ИЗМЕНЕНИЕ: Передаем user.getAuthorities() ---
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            user, // Principal: наш объект User, который теперь UserDetails
                            null, // Credentials: не нужны после валидации токена
                            user.getAuthorities() // Authorities: получаем из нашего объекта User
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    // Устанавливаем аутентификацию в SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            });
        }

        filterChain.doFilter(request, response);
    }
}
