package com.example.backend.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.backend.dto.PolygonAreaResponseDto;
import com.example.backend.dto.PolygonRequestDto;
import com.example.backend.entiity.PolygonArea;
import com.example.backend.entiity.User;
import com.example.backend.repository.PolygonAreaRepository;
import com.example.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/polygons")
@RequiredArgsConstructor
@Slf4j
public class PolygonAreaController {

    private final PolygonAreaRepository polygonRepo;
    private final UserRepository userRepo;

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

        User attachedUser = userRepo.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found in database."));

        log.info("PolygonAreaController: Authenticated user email: {}", attachedUser.getEmail());

        PolygonArea area = PolygonArea.builder()
                .id(UUID.randomUUID()) // Генерируем UUID здесь
                .name(dto.getName()) // Устанавливаем name из DTO
                .crop(dto.getCrop()) // Устанавливаем crop из DTO
                .user(attachedUser)
                .geoJson(dto.getGeoJson()) // GeoJSON как строка геометрии
                .build();

        try {
            polygonRepo.save(area);
            log.info("Polygon '{}' saved successfully for user {}. ID: {}", dto.getName(), attachedUser.getEmail(), area.getId());
            return ResponseEntity.ok(area.getId()); // Возвращаем UUID нового полигона
        } catch (Exception e) {
            log.error("PolygonAreaController: Error saving polygon '{}' for user {}: {}", dto.getName(), attachedUser.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(500).body("Failed to save polygon due to internal server error.");
        }
    }

    @GetMapping("/my")
    public ResponseEntity<List<PolygonAreaResponseDto>> getMyPolygons(@AuthenticationPrincipal User user) { 
        if (user == null) {
            return ResponseEntity.status(401).body(List.of()); 
        }

        List<PolygonArea> polygons = polygonRepo.findAllByUserId(user.getId());

        List<PolygonAreaResponseDto> dtos = polygons.stream()
                .map(PolygonAreaResponseDto::new)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePolygon(@PathVariable String id, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        UUID polygonId;
        try {
            polygonId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format received for deletion: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid polygon ID format.");
        }

        PolygonArea polygon = polygonRepo.findById(polygonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Polygon not found with ID: " + id));

        if (!polygon.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("Forbidden: You do not own this polygon.");
        }

        polygonRepo.deleteById(polygonId);
        log.info("Polygon with ID {} deleted successfully by user {}", polygonId, user.getEmail());
        return ResponseEntity.ok("Polygon deleted successfully");
    }

    @PutMapping("/{id}") // <-- Обновленный PUT эндпоинт
    public ResponseEntity<?> updatePolygon(
            @PathVariable String id, 
            @RequestBody PolygonRequestDto dto, 
            @AuthenticationPrincipal User user
    ) {
        log.info("PolygonAreaController: Received request to update polygon ID: {}", id);

        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        UUID polygonId;
        try {
            polygonId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format received for update: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid polygon ID format.");
        }

        PolygonArea existingPolygon = polygonRepo.findById(polygonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Polygon not found with ID: " + id));

        if (!existingPolygon.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("Forbidden: You do not own this polygon.");
        }

        // Обновляем поля name, crop, geoJson из DTO
        existingPolygon.setName(dto.getName());
        existingPolygon.setGeoJson(dto.getGeoJson()); 
        existingPolygon.setCrop(dto.getCrop()); 

        try {
            polygonRepo.save(existingPolygon); 
            log.info("Polygon '{}' with ID {} updated successfully for user {}.", dto.getName(), id, user.getEmail());
            return ResponseEntity.ok("Polygon updated successfully");
        } catch (Exception e) {
            log.error("Error updating polygon '{}' with ID {} for user {}: {}", dto.getName(), id, user.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(500).body("Failed to update polygon due to internal server error.");
        }
    }

    /**
     * Удаляет все полигоны для аутентифицированного пользователя.
     * Эндпоинт: DELETE /api/polygons/clear-all
     * @param user Аутентифицированный пользователь.
     * @return ResponseEntity с сообщением об успехе или ошибке.
     */
    @DeleteMapping("/clear-all")
    public ResponseEntity<?> clearAllPolygons(@AuthenticationPrincipal User user) {
        log.info("PolygonAreaController: Received request to clear all polygons for user.");
        if (user == null) {
            log.error("PolygonAreaController: @AuthenticationPrincipal User is NULL for clear-all request.");
            return ResponseEntity.status(401).body("Authentication required or user not found.");
        }

        try {
            // Находим все полигоны, принадлежащие текущему пользователю, и удаляем их
            List<PolygonArea> userPolygons = polygonRepo.findAllByUserId(user.getId());
            if (userPolygons.isEmpty()) {
                log.info("No polygons found to clear for user: {}", user.getEmail());
                return ResponseEntity.ok("No polygons to clear.");
            }

            polygonRepo.deleteAll(userPolygons); // Удаляем все найденные полигоны
            log.info("Successfully cleared {} polygons for user: {}", userPolygons.size(), user.getEmail());
            return ResponseEntity.ok("All polygons cleared successfully.");
        } catch (Exception e) {
            log.error("Error clearing all polygons for user {}: {}", user.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to clear all polygons due to internal server error.");
        }
    }
}
