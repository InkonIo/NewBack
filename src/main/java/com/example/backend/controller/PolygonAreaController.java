package com.example.backend.controller;

import com.example.backend.dto.PolygonRequestDto;
import com.example.backend.entiity.PolygonArea;
import com.example.backend.entiity.User;
import com.example.backend.repository.PolygonAreaRepository;
import com.example.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/polygons")
@RequiredArgsConstructor
@Slf4j
public class PolygonAreaController {

    private final PolygonAreaRepository polygonRepo;
    private final UserRepository userRepo; // <--- ДОБАВИЛ

    @PostMapping
    public ResponseEntity<?> createPolygon(
            @RequestBody PolygonRequestDto dto,
            @AuthenticationPrincipal User user
    ) {
        log.info("PolygonAreaController: Received request to create polygon. Name: {}", dto.getName());

        if (user == null) {
            log.error("PolygonAreaController: @AuthenticationPrincipal User is NULL.");
            return ResponseEntity.status(401).body("Authentication required or user not found.");
        }

        // 🔧 ВАЖНО: загружаем пользователя из базы, чтобы он был «attached»
        User attachedUser = userRepo.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found in database."));

        log.info("PolygonAreaController: Authenticated user email: {}", attachedUser.getEmail());

        PolygonArea area = PolygonArea.builder()
                .id(UUID.randomUUID())
                .name(dto.getName())
                .user(attachedUser) // 💡 тут используем связанного юзера
                .geoJson(dto.getGeoJson())
                .build();

        try {
            polygonRepo.save(area);
            log.info("PolygonAreaController: Polygon '{}' saved successfully for user {}.", dto.getName(), attachedUser.getEmail());
            return ResponseEntity.ok("Polygon saved");
        } catch (Exception e) {
            log.error("PolygonAreaController: Error saving polygon '{}' for user {}: {}", dto.getName(), attachedUser.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(500).body("Failed to save polygon due to internal server error.");
        }
    }
}
