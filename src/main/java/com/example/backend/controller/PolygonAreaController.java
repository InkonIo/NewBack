package com.example.backend.controller;

import com.example.backend.dto.PolygonRequestDto;
import com.example.backend.entiity.PolygonArea;
import com.example.backend.entiity.User;
import com.example.backend.repository.PolygonAreaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Добавьте этот импорт для логирования
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/polygons")
@RequiredArgsConstructor
@Slf4j // Аннотация Lombok для автоматического создания логгера
public class PolygonAreaController {

    private final PolygonAreaRepository polygonRepo;

    @PostMapping
    public ResponseEntity<?> createPolygon(
            @RequestBody PolygonRequestDto dto,
            @AuthenticationPrincipal User user // <--- Здесь мы ожидаем аутентифицированного пользователя
    ) {
        log.info("PolygonAreaController: Received request to create polygon. Name: {}", dto.getName());
        
        if (user == null) {
            log.error("PolygonAreaController: @AuthenticationPrincipal User is NULL. Cannot save polygon without an authenticated user.");
            // Вместо 500 Internal Server Error, лучше вернуть 401 Unauthorized или 403 Forbidden
            // Если этот эндпоинт требует аутентификации, то до сюда не должно доходить без аутентификации.
            // Но если дошло, это указывает на проблему с конфигурацией Security,
            // или то, что SecurityContextHolder не был установлен.
            return ResponseEntity.status(401).body("Authentication required or user not found.");
        }

        log.info("PolygonAreaController: Authenticated user email: {}", user.getEmail());

        PolygonArea area = PolygonArea.builder()
                .id(UUID.randomUUID())
                .name(dto.getName())
                .user(user) // Используем объект User
                .geoJson(dto.getGeoJson())
                .build();

        try {
            polygonRepo.save(area);
            log.info("PolygonAreaController: Polygon '{}' saved successfully for user {}.", dto.getName(), user.getEmail());
            return ResponseEntity.ok("Polygon saved");
        } catch (Exception e) {
            log.error("PolygonAreaController: Error saving polygon '{}' for user {}: {}", dto.getName(), user.getEmail(), e.getMessage(), e);
            // Если вы хотите дать более конкретный ответ клиенту,
            // можно проанализировать тип исключения (например, DataIntegrityViolationException)
            return ResponseEntity.status(500).body("Failed to save polygon due to internal server error.");
        }
    }
}
