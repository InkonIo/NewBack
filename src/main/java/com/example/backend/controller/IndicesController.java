package com.example.backend.controller;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.entiity.PolygonArea;
import com.example.backend.service.PolygonAreaService;
import com.example.backend.service.SentinelHubService;

@RestController
@RequestMapping("/api/v1/indices")
public class IndicesController {

    private final PolygonAreaService polygonAreaService;
    private final SentinelHubService sentinelHubService;

    public IndicesController(PolygonAreaService polygonAreaService, SentinelHubService sentinelHubService) {
        this.polygonAreaService = polygonAreaService;
        this.sentinelHubService = sentinelHubService;
    }

    @GetMapping("/ndvi/{polygonId}")
    public ResponseEntity<?> getNdvIForPolygon(@PathVariable String polygonId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(polygonId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("message", "Invalid polygon ID format. Must be a valid UUID."));
        }

        Optional<PolygonArea> optionalPolygon;
        try {
            optionalPolygon = polygonAreaService.getPolygonByIdForCurrentUser(uuid);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", "Access denied. " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }

        if (optionalPolygon.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Polygon not found with ID: " + polygonId));
        }

        PolygonArea polygon = optionalPolygon.get();
        String geoJsonString = polygon.getGeoJson();

        if (geoJsonString == null || geoJsonString.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("message", "Polygon GeoJSON data is missing for ID: " + polygonId));
        }

        try {
            LocalDate toDate = LocalDate.now();
            LocalDate fromDate = toDate.minusDays(30);

            Double ndviMean = sentinelHubService.getNdvIStatistics(geoJsonString, fromDate, toDate);

            String interpretation;
            if (ndviMean == null) {
                interpretation = "Не удалось получить значение NDVI. Пожалуйста, попробуйте еще раз.";
            } else if (ndviMean > 0.4) {
                interpretation = "На этом участке NDVI (Normalized Difference Vegetation Index) равен " + String.format("%.3f", ndviMean) + ", что указывает на хорошее состояние растительности. 🌿";
            } else if (ndviMean > 0.2) {
                interpretation = "На этом участке NDVI (Normalized Difference Vegetation Index) равен " + String.format("%.3f", ndviMean) + ", что говорит об умеренном состоянии растительности. 🌾";
            } else if (ndviMean >= 0) {
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

    // НОВЫЙ ЭНДПОИНТ для динамического получения NDVI по координатам
    // Фронтенд будет отправлять сюда POST-запрос с { lat, lon }
    @PostMapping("/ndvi")
    public ResponseEntity<?> getNdvIForCoordinates(@RequestBody Map<String, Double> coords) {
        Double lat = coords.get("lat");
        Double lon = coords.get("lon");

        if (lat == null || lon == null) {
            return ResponseEntity.status(400).body(Map.of("error", "Latitude and Longitude are required."));
        }

        try {
            // --- ВРЕМЕННЫЙ МОК ДЛЯ ОБХОДА ОГРАНИЧЕНИЙ SENTINEL HUB ---
            // Активируйте эту строку и закомментируйте блок реального запроса к Sentinel Hub ниже,
            // чтобы использовать моковые данные NDVI.
            return ResponseEntity.ok(Map.of("ndvi", (Math.random() * 2 - 1))); // <-- ЭТА СТРОКА ТЕПЕРЬ АКТИВНА!
            // --- КОНЕЦ ВРЕМЕННОГО МОКА ---

            /*
            // --- РЕАЛЬНЫЙ ЗАПРОС К SENTINEL HUB ---
            // Закомментируйте строку мока выше и раскомментируйте этот блок,
            // когда вы решите проблему с лимитами Sentinel Hub или недоступностью данных.
            LocalDate toDate = LocalDate.now();
            LocalDate fromDate = toDate.minusDays(30);

            // Создаем маленький полигон (квадрат) вокруг переданной точки
            double buffer = 0.0001; // Примерно 10-15 метров
            String geoJsonPointAsPolygon = String.format(
                "{\"type\":\"Polygon\",\"coordinates\":[[[%f,%f],[%f,%f],[%f,%f],[%f,%f],[%f,%f]]]}",
                lon - buffer, lat - buffer, // bottom-left
                lon + buffer, lat - buffer, // bottom-right
                lon + buffer, lat + buffer, // top-right
                lon - buffer, lat + buffer, // top-left
                lon - buffer, lat - buffer  // close the polygon
            );

            Double ndviMean = sentinelHubService.getNdvIStatistics(geoJsonPointAsPolygon, fromDate, toDate);

            return ResponseEntity.ok(Map.of("ndvi", ndviMean));
            // --- КОНЕЦ РЕАЛЬНОГО ЗАПРОСА ---
            */
        } catch (Exception e) {
            System.err.println("Error fetching NDVI for coordinates " + lat + ", " + lon + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to retrieve NDVI data for point: " + e.getMessage()));
        }
    }
}

// 