package com.example.backend.controller;

import java.time.LocalDate; // Изменен импорт на PolygonArea
import java.util.Map; // Изменен импорт на PolygonAreaService
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController; // Добавлен импорт для UUID

import com.example.backend.entiity.PolygonArea;
import com.example.backend.service.PolygonAreaService;
import com.example.backend.service.SentinelHubService;

@RestController
@RequestMapping("/api/v1/indices")
public class IndicesController {

    private final PolygonAreaService polygonAreaService; // Изменено с polygonService на polygonAreaService
    private final SentinelHubService sentinelHubService;

    // Обновлен конструктор для использования PolygonAreaService
    public IndicesController(PolygonAreaService polygonAreaService, SentinelHubService sentinelHubService) {
        this.polygonAreaService = polygonAreaService;
        this.sentinelHubService = sentinelHubService;
    }

    @GetMapping("/ndvi/{polygonId}")
    public ResponseEntity<?> getNdvIForPolygon(@PathVariable String polygonId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(polygonId); // Преобразуем String ID в UUID
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("message", "Invalid polygon ID format. Must be a valid UUID."));
        }

        // Используем getPolygonByIdForCurrentUser для проверки принадлежности полигона
        Optional<PolygonArea> optionalPolygon;
        try {
            optionalPolygon = polygonAreaService.getPolygonByIdForCurrentUser(uuid);
        } catch (SecurityException e) {
            // Обработка случая, когда полигон не принадлежит текущему пользователю
            return ResponseEntity.status(403).body(Map.of("message", "Access denied. " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            // Обработка случая, когда полигон не найден
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }


        if (optionalPolygon.isEmpty()) {
            // Этот блок должен быть достигнут только если getPolygonByIdForCurrentUser не выбросил исключение,
            // но полигона все равно нет (хотя логика сервиса должна это предотвратить).
            // Добавляем для дополнительной безопасности.
            return ResponseEntity.status(404).body(Map.of("message", "Polygon not found with ID: " + polygonId));
        }

        PolygonArea polygon = optionalPolygon.get();
        String geoJsonString = polygon.getGeoJson();

        if (geoJsonString == null || geoJsonString.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("message", "Polygon GeoJSON data is missing for ID: " + polygonId));
        }

        try {
            // Определяем временной диапазон (например, последние 30 дней)
            LocalDate toDate = LocalDate.now();
            LocalDate fromDate = toDate.minusDays(30);

            Double ndviMean = sentinelHubService.getNdvIStatistics(geoJsonString, fromDate, toDate);

            String interpretation;
            if (ndviMean == null) {
                interpretation = "Не удалось получить значение NDVI. Пожалуйста, попробуйте еще раз.";
            } else if (ndviMean > 0.4) { // Произвольный порог для "хорошо"
                interpretation = "На этом участке NDVI (Normalized Difference Vegetation Index) равен " + String.format("%.3f", ndviMean) + ", что указывает на хорошее состояние растительности. 🌿";
            } else if (ndviMean > 0.2) {
                interpretation = "На этом участке NDVI (Normalized Difference Vegetation Index) равен " + String.format("%.3f", ndviMean) + ", что говорит об умеренном состоянии растительности. 🌾";
            }
             else if (ndviMean >= 0) {
                interpretation = "На этом участке NDVI (Normalized Difference Vegetation Index) равен " + String.format("%.3f", ndviMean) + ", что указывает на скудную растительность или её отсутствие. 🍂";
            } else {
                interpretation = "На этом участке NDVI (Normalized Difference Vegetation Index) равен " + String.format("%.3f", ndviMean) + ", что, скорее всего, указывает на воду, снег, облака или нерастительные объекты. 💧";
            }

            return ResponseEntity.ok(Map.of("ndviValue", ndviMean, "interpretation", interpretation));

        } catch (Exception e) {
            System.err.println("Error fetching NDVI for polygon ID " + polygonId + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Failed to retrieve NDVI data: " + e.getMessage()));
        }
    }
}
