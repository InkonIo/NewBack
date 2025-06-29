package com.example.backend.controller;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap; // Импорт для кэша

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl; // Импорт для CacheControl
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit; // Импорт для TimeUnit

@RestController
@RequestMapping("/api/v1/indices")
public class IndicesController {

    private final PolygonAreaService polygonAreaService;
    private final SentinelHubService sentinelHubService;
    private final RestTemplate restTemplate;

    @Value("${sentinelhub.auth.url}")
    private String authUrl;

    @Value("${sentinelhub.process.url}")
    private String processUrl;

    @Value("${sentinelhub.client.id}")
    private String clientId;

    @Value("${sentinelhub.client.secret}")
    private String clientSecret;

    // ✅ НОВЫЙ ВНУТРЕННИЙ КЛАСС ДЛЯ ЭЛЕМЕНТОВ КЭША
    private static class CachedTile {
        private final byte[] data;
        private final MediaType contentType;
        private final long expiryTimeMillis;

        public CachedTile(byte[] data, MediaType contentType, long expiryDurationSeconds) {
            this.data = data;
            this.contentType = contentType;
            this.expiryTimeMillis = System.currentTimeMillis() + (expiryDurationSeconds * 1000);
        }

        public byte[] getData() {
            return data;
        }

        public MediaType getContentType() {
            return contentType;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expiryTimeMillis;
        }
    }

    // ✅ КЭШ ДЛЯ WMS-ТАЙЛОВ
    private final Map<String, CachedTile> wmsTileCache = new ConcurrentHashMap<>();
    private static final long TILE_CACHE_EXPIRY_SECONDS = 300; // Кэшировать тайлы на 5 минут

    public IndicesController(PolygonAreaService polygonAreaService, SentinelHubService sentinelHubService, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.polygonAreaService = polygonAreaService;
        this.sentinelHubService = sentinelHubService;
        this.restTemplate = restTemplate;
        if (!restTemplate.getMessageConverters().stream().anyMatch(converter -> converter instanceof ByteArrayHttpMessageConverter)) {
            restTemplate.getMessageConverters().add(new ByteArrayHttpMessageConverter());
            System.out.println("ByteArrayHttpMessageConverter added to RestTemplate.");
        }
    }

    @GetMapping("/ndvi/{polygonId}")
    public ResponseEntity<?> getNdvIForPolygon(@PathVariable String polygonId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(polygonId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("message", "Неверный формат ID полигона. Должен быть действительный UUID."));
        }

        Optional<PolygonArea> optionalPolygon;
        try {
            optionalPolygon = polygonAreaService.getPolygonByIdForCurrentUser(uuid);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", "Доступ запрещен. " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }

        if (optionalPolygon.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Полигон не найден с ID: " + polygonId));
        }

        PolygonArea polygon = optionalPolygon.get();
        String geoJsonString = polygon.getGeoJson();

        if (geoJsonString == null || geoJsonString.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("message", "Отсутствуют данные GeoJSON для полигона с ID: " + polygonId));
        }

        try {
            LocalDate toDate = LocalDate.now();
            LocalDate fromDate = toDate.minusDays(30);

            Double ndviMean = sentinelHubService.getNdvIStatistics(geoJsonString, fromDate, toDate);

            String interpretation;
            if (ndviMean == null) {
                interpretation = "Не удалось получить значение NDVI. Пожалуйста, попробуйте еще раз. Возможно, нет чистых изображений для выбранного периода или координат.";
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
            System.err.println("Ошибка получения NDVI для полигона ID " + polygonId + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Не удалось получить данные NDVI: " + e.getMessage()));
        }
    }

    @PostMapping("/ndvi")
    public ResponseEntity<?> getNdvIForCoordinates(@RequestBody Map<String, Double> coords) {
        Double lat = coords.get("lat");
        Double lon = coords.get("lon");

        if (lat == null || lon == null) {
            return ResponseEntity.status(400).body(Map.of("error", "Требуются широта и долгота."));
        }

        try {
            return ResponseEntity.ok(Map.of("ndvi", (Math.random() * 2 - 1)));
        } catch (Exception e) {
            System.err.println("Ошибка получения NDVI для координат " + lat + ", " + lon + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Не удалось получить данные NDVI для точки: " + e.getMessage()));
        }
    }

    @GetMapping("/wms-proxy/{instanceId}")
    public ResponseEntity<byte[]> proxyWms(@PathVariable String instanceId, @RequestParam Map<String, String> allRequestParams) {
        if (!instanceId.equals("f15c44d0-bbb8-4c66-b94e-6a8c7ab39349")) {
            System.err.println("WMS Proxy: Неверный instanceId: " + instanceId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        String sentinelHubWmsBaseUrl = "https://services.sentinel-hub.com/ogc/wms/" + instanceId;

        StringBuilder paramsBuilder = new StringBuilder();
        allRequestParams.forEach((key, value) -> {
            paramsBuilder.append(key).append("=").append(value).append("&");
        });
        if (paramsBuilder.length() > 0) {
            paramsBuilder.deleteCharAt(paramsBuilder.length() - 1);
        }

        String fullSentinelHubUrl = sentinelHubWmsBaseUrl + "?" + paramsBuilder.toString();
        System.out.println("WMS Proxy: Перенаправляем запрос к Sentinel Hub: " + fullSentinelHubUrl);

        // ✅ ПОПЫТКА ПОЛУЧИТЬ ИЗ КЭША
        CachedTile cachedTile = wmsTileCache.get(fullSentinelHubUrl);
        if (cachedTile != null && !cachedTile.isExpired()) {
            System.out.println("WMS Proxy: Возвращаем тайл из кэша для URL: " + fullSentinelHubUrl);
            HttpHeaders cachedHeaders = new HttpHeaders();
            cachedHeaders.setContentType(cachedTile.getContentType());
            cachedHeaders.setAccessControlAllowOrigin("http://localhost:5173");
            // Добавляем Cache-Control для браузерного кэширования
            cachedHeaders.setCacheControl(CacheControl.maxAge(TILE_CACHE_EXPIRY_SECONDS, TimeUnit.SECONDS).cachePublic());
            return new ResponseEntity<>(cachedTile.getData(), cachedHeaders, HttpStatus.OK);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.IMAGE_PNG));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                fullSentinelHubUrl,
                HttpMethod.GET,
                entity,
                byte[].class
            );

            System.out.println("WMS Proxy: Получен ответ от Sentinel Hub. Статус: " + response.getStatusCode());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // ✅ СОХРАНЯЕМ В КЭШ
                CachedTile newCachedTile = new CachedTile(response.getBody(), response.getHeaders().getContentType(), TILE_CACHE_EXPIRY_SECONDS);
                wmsTileCache.put(fullSentinelHubUrl, newCachedTile);
                System.out.println("WMS Proxy: Сохранили тайл в кэш для URL: " + fullSentinelHubUrl);

                HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.setContentType(MediaType.IMAGE_PNG);
                responseHeaders.setAccessControlAllowOrigin("http://localhost:5173");
                // ✅ ДОБАВЛЯЕМ Cache-Control ДЛЯ БРАУЗЕРНОГО КЭШИРОВАНИЯ
                responseHeaders.setCacheControl(CacheControl.maxAge(TILE_CACHE_EXPIRY_SECONDS, TimeUnit.SECONDS).cachePublic());

                return new ResponseEntity<>(response.getBody(), responseHeaders, HttpStatus.OK);
            } else {
                String errorBody = response.getBody() != null ? new String(response.getBody()) : "No body";
                System.err.println("WMS Proxy: Не удалось проксировать WMS-запрос. Статус: " + response.getStatusCode() + ". Тело ответа: " + errorBody);
                return ResponseEntity.status(response.getStatusCode()).body(null);
            }

        } catch (HttpClientErrorException e) {
            System.err.println("WMS Proxy: Ошибка HTTP-клиента при запросе к Sentinel Hub: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            e.printStackTrace();
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            System.err.println("WMS Proxy: Критическая ошибка во время WMS прокси-запроса: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}