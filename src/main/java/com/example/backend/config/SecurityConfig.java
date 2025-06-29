package com.example.backend.config;

import com.example.backend.JWT.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration // Указывает Spring, что это класс конфигурации
@RequiredArgsConstructor // Автоматически генерирует конструктор для финальных полей
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter; // Фильтр для обработки JWT токенов
    private final UserDetailsService userDetailsService; // Сервис для загрузки данных пользователя

    /**
     * Определяет цепочку фильтров безопасности HTTP.
     *
     * @param http Объект HttpSecurity для настройки безопасности.
     * @return SecurityFilterChain настроенную цепочку фильтров.
     * @throws Exception Если происходит ошибка при настройке.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable() // Отключаем CSRF-защиту, так как используем токены (STATELESS)
            .cors().configurationSource(corsConfigurationSource()) // ✅ Подключаем CORS-конфигурацию
            .and()
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    // Пути, доступные БЕЗ АУТЕНТИКАЦИИ (permitAll)
                    "/swagger-ui/**",          // Документация Swagger UI
                    "/v3/api-docs/**",         // OpenAPI спецификация
                    "/swagger-resources/**",   // Ресурсы Swagger
                    "/webjars/**",             // Webjars (для Swagger и других)
                    "/api/v1/auth/**",         // Эндпоинты аутентификации (регистрация, вход)
                    "/api/v1/recovery/**",     // Эндпоинты восстановления пароля
                    "/api/polygons",           // Этот путь был в вашем `requestMatchers`. Если `/api/polygons` не требует аутентификации для POST/GET, то оставляем. Если только для зарегистрированных, то удалить.
                    // ✅ ТОЧНЫЙ ПУТЬ К ЭНДПОИНТУ NDVI, который использует фронтенд
                    "/api/v1/indices/ndvi",
                    // ✅ Добавлено для потенциальных вложенных путей или параметров после /ndvi
                    "/api/v1/indices/ndvi/**",
                    "/api/v1/indices/wms-proxy/**",
                    "/",                       // Корневой путь (например, главная страница)
                    "/error"                   // Страница ошибок
                ).permitAll() // Разрешаем доступ без аутентификации
                .anyRequest().authenticated() // Все остальные запросы требуют аутентификации
            )
            .sessionManagement(sess -> sess
                // Устанавливаем политику управления сессиями как STATELESS (без сохранения состояния сессии),
                // так как используем JWT для аутентификации
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            // Добавляем наш JWT-фильтр перед стандартным фильтром аутентификации по имени пользователя и паролю
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .formLogin().disable() // Отключаем стандартную форму входа Spring Security
            .httpBasic().disable(); // Отключаем базовую HTTP-аутентификацию

        return http.build();
    }

    /**
     * Конфигурация CORS (Cross-Origin Resource Sharing).
     * Разрешает запросы с определенных источников (фронтенда).
     *
     * @return CorsConfigurationSource для настройки CORS.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://localhost:5173", // ✅ Ваш фронтенд на локальном хосте
            "https://agrofarm.kz",   // ✅ Ваш продакшен-домен
            "https://www.agrofarm.kz" // ✅ Ваш продакшен-домен с www
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")); // Разрешенные HTTP методы
        config.setAllowedHeaders(List.of("*")); // Разрешенные заголовки
        config.setAllowCredentials(true); // ✅ Разрешить отправку куки и заголовков авторизации (например, JWT)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // Применяем эту CORS-конфигурацию ко всем путям
        return source;
    }

    /**
     * Определяет провайдер аутентификации.
     *
     * @return AuthenticationProvider для аутентификации пользователей.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService); // Используем наш UserDetailsService
        authProvider.setPasswordEncoder(passwordEncoder());     // Используем наш PasswordEncoder
        return authProvider;
    }

    /**
     * Определяет менеджер аутентификации, который управляет процессом аутентификации.
     *
     * @param config Конфигурация аутентификации.
     * @return AuthenticationManager.
     * @throws Exception Если происходит ошибка при получении менеджера аутентификации.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Определяет кодировщик паролей (BCryptPasswordEncoder).
     *
     * @return PasswordEncoder для хэширования паролей.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
