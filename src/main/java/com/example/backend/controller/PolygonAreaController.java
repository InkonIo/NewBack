// src/main/java/com/example/backend/controller/PolygonAreaController.java
package com.example.backend.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
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

import com.example.backend.dto.PolygonAreaResponseDto;
import com.example.backend.dto.PolygonRequestDto;
import com.example.backend.entiity.PolygonArea;
import com.example.backend.entiity.User;
import com.example.backend.repository.PolygonAreaRepository;
import com.example.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/polygons")
@Slf4j // Для логирования
public class PolygonAreaController {

    @Autowired
    private PolygonAreaRepository polygonRepo;

    @Autowired
    private UserRepository userRepo; // Для получения пользователя, если нужно

    private final ObjectMapper objectMapper = new ObjectMapper(); // Для парсинга GeoJSON

    // Метод для создания нового полигона
    @PostMapping
    public ResponseEntity<?> createPolygonArea(@RequestBody PolygonRequestDto requestDto, @AuthenticationPrincipal User user) {
        log.info("PolygonAreaController: Received request to create polygon.");

        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        try {
            // Фронтенд теперь отправляет все данные в поле geoJson
            String geoJsonString = requestDto.getGeoJson();
            // Нам не нужно извлекать name и crop здесь, просто сохраняем весь geoJson
            
            PolygonArea newPolygon = PolygonArea.builder()
                    .id(UUID.randomUUID()) // Генерируем новый UUID для ID полигона
                    .user(user)
                    .geoJson(geoJsonString) // Сохраняем всю строку GeoJSON, как пришло с фронтенда
                    .build();

            PolygonArea savedPolygon = polygonRepo.save(newPolygon);
            log.info("Polygon created successfully with ID: {}", savedPolygon.getId());
            // Возвращаем ID нового полигона в ответе, возможно в DTO или как строку
            return ResponseEntity.status(HttpStatus.CREATED).body(savedPolygon.getId().toString()); // Возвращаем ID как строку
        } catch (Exception e) {
            log.error("Error creating polygon: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating polygon: " + e.getMessage());
        }
    }

    // Метод для обновления существующего полигона
    @PutMapping("/{polygonId}")
    public ResponseEntity<?> updatePolygonArea(@PathVariable UUID polygonId, @RequestBody PolygonRequestDto requestDto, @AuthenticationPrincipal User user) {
        log.info("PolygonAreaController: Received request to update polygon with ID: {}", polygonId);

        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        return polygonRepo.findById(polygonId).map(polygon -> {
            if (!polygon.getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not authorized to update this polygon.");
            }

            try {
                // Фронтенд теперь отправляет все данные в поле geoJson
                String newGeoJsonString = requestDto.getGeoJson();
                polygon.setGeoJson(newGeoJsonString); // Обновляем всю строку GeoJSON
                
                // Нам не нужно извлекать name и crop здесь, просто обновляем весь geoJson

                polygonRepo.save(polygon);
                log.info("Polygon updated successfully with ID: {}", polygonId);
                return ResponseEntity.ok("Polygon updated successfully.");
            } catch (Exception e) {
                log.error("Error updating polygon {}: {}", polygonId, e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating polygon: " + e.getMessage());
            }
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Polygon not found."));
    }

    // Метод для получения полигона по ID
    @GetMapping("/{polygonId}")
    public ResponseEntity<?> getPolygonAreaById(@PathVariable UUID polygonId, @AuthenticationPrincipal User user) {
        log.info("PolygonAreaController: Received request to get polygon with ID: {}", polygonId);
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        return polygonRepo.findById(polygonId)
                .map(polygon -> {
                    if (!polygon.getUser().getId().equals(user.getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not authorized to view this polygon.");
                    }
                    return ResponseEntity.ok(new PolygonAreaResponseDto(polygon));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Polygon not found."));
    }

    // Метод для получения всех полигонов пользователя
    @GetMapping("/my")
    public ResponseEntity<?> getAllPolygonsForUser(@AuthenticationPrincipal User user) {
        log.info("PolygonAreaController: Received request to get all polygons for user.");
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        List<PolygonArea> polygons = polygonRepo.findAllByUserId(user.getId());
        List<PolygonAreaResponseDto> responseDtos = polygons.stream()
                .map(PolygonAreaResponseDto::new)
                .collect(Collectors.toList());
        log.info("Found {} polygons for user {}.", responseDtos.size(), user.getEmail());
        return ResponseEntity.ok(responseDtos);
    }
    
    // Метод для удаления полигона по ID
    @DeleteMapping("/{polygonId}")
    public ResponseEntity<?> deletePolygonArea(@PathVariable UUID polygonId, @AuthenticationPrincipal User user) {
        log.info("PolygonAreaController: Received request to delete polygon with ID: {}", polygonId);

        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        return polygonRepo.findById(polygonId).map(polygon -> {
            if (!polygon.getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not authorized to delete this polygon.");
            }
            polygonRepo.delete(polygon);
            log.info("Polygon deleted successfully with ID: {}", polygonId);
            return ResponseEntity.ok("Polygon deleted successfully.");
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Polygon not found."));
    }

    // Метод для очистки всех полигонов пользователя
    @DeleteMapping("/clear-all")
    public ResponseEntity<?> deleteAllPolygonsForUser(@AuthenticationPrincipal User user) {
        log.info("PolygonAreaController: Received request to clear all polygons for user.");

        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        try {
            List<PolygonArea> userPolygons = polygonRepo.findAllByUserId(user.getId());
            if (userPolygons.isEmpty()) {
                log.info("No polygons found to delete for user {}.", user.getEmail());
                return ResponseEntity.ok("No polygons found to delete.");
            }
            polygonRepo.deleteAll(userPolygons);
            log.info("All polygons cleared successfully for user {}.", user.getEmail());
            return ResponseEntity.ok("All polygons deleted successfully.");
        } catch (Exception e) {
            log.error("Error clearing all polygons for user {}: {}", user.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to clear all polygons due to internal server error.");
        }
    }
}