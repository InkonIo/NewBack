package com.example.backend.controller;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.example.backend.entiity.PolygonArea;
import com.example.backend.service.PolygonAreaService;
import com.example.backend.service.SentinelHubService;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/indices")
@CrossOrigin(origins = "*") // Для CORS
public class IndicesController {

    private static final Logger logger = LoggerFactory.getLogger(IndicesController.class);

    private final PolygonAreaService polygonAreaService;
    private final SentinelHubService sentinelHubService;
    private final RestTemplate restTemplate;

    @Value("${sentinelhub.wms.instance.id}")
    private String wmsInstanceId;

    private static final long TILE_CACHE_EXPIRY_SECONDS = 300;

    private static class CachedTile {
        private final byte[] data;
        private final MediaType contentType;
        private final long expiryTimeMillis;

        public CachedTile(byte[] data, MediaType contentType, long expiryDurationSeconds) {
            this.data = data;
            this.contentType = contentType;
            this.expiryTimeMillis = System.currentTimeMillis() + expiryDurationSeconds * 1000;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expiryTimeMillis;
        }

        public byte[] getData() {
            return data;
        }

        public MediaType getContentType() {
            return contentType;
        }
    }

    private final Map<String, CachedTile> wmsTileCache = new ConcurrentHashMap<>();

    public IndicesController(PolygonAreaService polygonAreaService,
                             SentinelHubService sentinelHubService,
                             RestTemplate restTemplate,
                             ObjectMapper objectMapper) {
        this.polygonAreaService = polygonAreaService;
        this.sentinelHubService = sentinelHubService;
        this.restTemplate = restTemplate;

        // Убедимся, что RestTemplate может обрабатывать byte[]
        if (restTemplate.getMessageConverters().stream()
                .noneMatch(converter -> converter instanceof ByteArrayHttpMessageConverter)) {
            restTemplate.getMessageConverters().add(new ByteArrayHttpMessageConverter());
        }
    }

    @GetMapping("/ndvi/{polygonId}") // Этот эндпоинт остается для статистики NDVI
    public ResponseEntity<?> getNdvIForPolygon(@PathVariable String polygonId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(polygonId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Неверный формат UUID."));
        }

        Optional<PolygonArea> optionalPolygon;
        try {
            optionalPolygon = polygonAreaService.getPolygonByIdForCurrentUser(uuid);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }

        if (optionalPolygon.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Полигон не найден."));
        }

        String geoJson = optionalPolygon.get().getGeoJson();
        if (geoJson == null || geoJson.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "GeoJSON пуст."));
        }

        try {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(30);

            Double ndvi = sentinelHubService.getNdvIStatistics(geoJson, from, to);
            String interpretation = interpretNdvi(ndvi);

            return ResponseEntity.ok(Map.of(
                    "ndviValue", ndvi,
                    "interpretation", interpretation
            ));
        } catch (Exception e) {
            logger.error("Ошибка при получении NDVI для полигона {}: {}", polygonId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Ошибка при получении NDVI: " + e.getMessage()));
        }
    }

    private String interpretNdvi(Double ndvi) {
        if (ndvi == null) {
            return "Не удалось получить значение NDVI.";
        } else if (ndvi > 0.4) {
            return String.format("NDVI: %.3f — высокая растительность 🌿", ndvi);
        } else if (ndvi > 0.2) {
            return String.format("NDVI: %.3f — умеренная растительность 🌾", ndvi);
        } else if (ndvi >= 0) {
            return String.format("NDVI: %.3f — низкая растительность 🍂", ndvi);
        } else {
            return String.format("NDVI: %.3f — вода, снег или объекты 💧", ndvi);
        }
    }

    @PostMapping("/ndvi") // Этот эндпоинт остается для статистики NDVI по координатам
    public ResponseEntity<?> getNdvIForCoordinates(@RequestBody Map<String, Double> coords) {
        Double lat = coords.get("lat");
        Double lon = coords.get("lon");

        if (lat == null || lon == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Требуются координаты: lat и lon."));
        }

        try {
            // В реальном приложении здесь можно вызвать SentinelHubService для получения NDVI точки,
            // используя Process API с очень маленьким bbox вокруг точки.
            return ResponseEntity.ok(Map.of("ndvi", (Math.random() * 2 - 1))); // Пока заглушка
        } catch (Exception e) {
            logger.error("Ошибка получения NDVI для координат ({}, {}): {}", lat, lon, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Ошибка получения NDVI для координат."));
        }
    }

    // ✅ НОВЫЙ ЭНДПОИНТ: для получения маскированного изображения любого индекса
    @GetMapping("/masked-index/{polygonId}/{layerId}")
    public ResponseEntity<byte[]> getMaskedIndexImage(@PathVariable String polygonId, @PathVariable String layerId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(polygonId);
        } catch (IllegalArgumentException e) {
            logger.error("Неверный формат UUID для маскированного изображения: {}", polygonId);
            return ResponseEntity.badRequest().build();
        }

        Optional<PolygonArea> optionalPolygon;
        try {
            optionalPolygon = polygonAreaService.getPolygonByIdForCurrentUser(uuid);
        } catch (SecurityException e) {
            logger.error("Ошибка безопасности при получении полигона {}: {}", polygonId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            logger.error("Полигон {} не найден: {}", polygonId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (optionalPolygon.isEmpty()) {
            logger.warn("Полигон {} не найден для текущего пользователя для маскированного изображения слоя {}.", polygonId, layerId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        try {
            // Используем новый универсальный метод
            byte[] image = sentinelHubService.getMaskedImage(optionalPolygon.get().getGeoJson(), layerId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            return new ResponseEntity<>(image, headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            logger.error("Неподдерживаемый Layer ID для маскированного изображения {}: {}", layerId, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            // ✅ ИСПРАВЛЕНО: Логируем полную трассировку стека и возвращаем детальное сообщение
            logger.error("Ошибка при получении маскированного изображения слоя {} для полигона {}: {}", layerId, polygonId, e.getMessage(), e);
            String errorMessage = "Ошибка при получении маскированного изображения: " + e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/wms-proxy/{instanceId}")
    public ResponseEntity<byte[]> proxyWms(@PathVariable String instanceId,
                                           @RequestParam Map<String, String> allRequestParams) {
        if (!instanceId.equals(wmsInstanceId)) {
            String errorMessage = "Неверный Instance ID в запросе. Ожидается: " + wmsInstanceId + ", Получено: " + instanceId;
            logger.error(errorMessage);
            return ResponseEntity.badRequest().body(errorMessage.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        String baseUrl = "https://services.sentinel-hub.com/ogc/wms/" + wmsInstanceId;
        // Строим полный URL для запроса к Sentinel Hub WMS
        String fullUrl = baseUrl + "?" + allRequestParams.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + "&" + b)
                .orElse("");

        // Проверка кэша
        CachedTile cached = wmsTileCache.get(fullUrl);
        if (cached != null && !cached.isExpired()) {
            HttpHeaders cachedHeaders = new HttpHeaders();
            cachedHeaders.setContentType(cached.getContentType());
            cachedHeaders.setCacheControl(CacheControl.maxAge(TILE_CACHE_EXPIRY_SECONDS, TimeUnit.SECONDS).cachePublic());
            return new ResponseEntity<>(cached.getData(), cachedHeaders, HttpStatus.OK);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.IMAGE_PNG)); // WMS обычно возвращает PNG/JPEG

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            logger.info("Отправка WMS запроса в Sentinel Hub: {}", fullUrl); // Логируем полный URL запроса
            ResponseEntity<byte[]> response = restTemplate.exchange(
                fullUrl, HttpMethod.GET, entity, byte[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.info("Успешно получен WMS ответ от Sentinel Hub. Размер: {} байт.", response.getBody().length);
                wmsTileCache.put(fullUrl, new CachedTile(response.getBody(), MediaType.IMAGE_PNG, TILE_CACHE_EXPIRY_SECONDS));
                HttpHeaders respHeaders = new HttpHeaders();
                respHeaders.setContentType(MediaType.IMAGE_PNG);
                respHeaders.setCacheControl(CacheControl.maxAge(TILE_CACHE_EXPIRY_SECONDS, TimeUnit.SECONDS).cachePublic());
                return new ResponseEntity<>(response.getBody(), respHeaders, HttpStatus.OK);
            } else {
                logger.error("Получен неуспешный WMS ответ от Sentinel Hub: Статус {}", response.getStatusCode());
                return ResponseEntity.status(response.getStatusCode()).build();
            }

        } catch (HttpClientErrorException e) {
            String errorResponseBody = (e.getResponseBodyAsByteArray() != null) ? new String(e.getResponseBodyAsByteArray(), java.nio.charset.StandardCharsets.UTF_8) : "No response body";
            logger.error("HTTP client error при запросе WMS к Sentinel Hub: Статус {} - {}", e.getStatusCode(), errorResponseBody);
            return ResponseEntity.status(e.getStatusCode()).body((e.getResponseBodyAsByteArray() != null) ? e.getResponseBodyAsByteArray() : new byte[0]);
        } catch (Exception e) {
            logger.error("Непредвиденная ошибка при проксировании WMS запроса: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
