package com.example.backend.controller;

import com.example.backend.dto.PolygonRequestDto;
import com.example.backend.entiity.PolygonArea;
import com.example.backend.entiity.User;
import com.example.backend.repository.PolygonAreaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/polygons") // ← Не забудь это
@RequiredArgsConstructor
public class PolygonAreaController {

    private final PolygonAreaRepository polygonRepo;

    @PostMapping
    public ResponseEntity<?> createPolygon(
            @RequestBody PolygonRequestDto dto,
            @AuthenticationPrincipal User user
    ) {
        PolygonArea area = PolygonArea.builder()
                .id(UUID.randomUUID())
                .name(dto.getName())
                .user(user)
                .geoJson(dto.getGeoJson()) // ← GeoJSON строка
                .build();

        polygonRepo.save(area);
        return ResponseEntity.ok("Polygon saved");
    }
}
