package com.example.backend.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.example.backend.dto.SentinelHubAuthResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper; // Импорт для обработки исключений JSON

@Service
public class SentinelHubService {

    private static final Logger logger = LoggerFactory.getLogger(SentinelHubService.class);
    private static final long TOKEN_EXPIRY_BUFFER_MILLIS = 5 * 60 * 1000;

    @Value("${sentinelhub.auth.url}")
    private String authUrl;

    @Value("${sentinelhub.statistics.url}") 
    private String statisticsUrl; 

    @Value("${sentinelhub.process.image.url}") 
    private String processImageUrl; 

    @Value("${sentinelhub.client.id}")
    private String clientId;

    @Value("${sentinelhub.client.secret}")
    private String clientSecret;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private volatile String cachedAccessToken;
    private volatile long tokenExpiryTimeMillis;
    private final Object tokenRefreshLock = new Object();

    public SentinelHubService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    private String getOrCreateAccessToken() {
        if (cachedAccessToken == null || System.currentTimeMillis() >= tokenExpiryTimeMillis - TOKEN_EXPIRY_BUFFER_MILLIS) {
            synchronized (tokenRefreshLock) {
                if (cachedAccessToken == null || System.currentTimeMillis() >= tokenExpiryTimeMillis - TOKEN_EXPIRY_BUFFER_MILLIS) {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

                    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
                    body.add("grant_type", "client_credentials");
                    body.add("client_id", clientId);
                    body.add("client_secret", clientSecret);

                    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

                    try {
                        ResponseEntity<SentinelHubAuthResponse> response = restTemplate.postForEntity(authUrl, request, SentinelHubAuthResponse.class);
                        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                            SentinelHubAuthResponse authResponse = response.getBody();
                            cachedAccessToken = authResponse.getAccessToken();
                            tokenExpiryTimeMillis = System.currentTimeMillis() + (authResponse.getExpiresIn() * 1000L);
                            logger.info("Sentinel Hub access token refreshed. Expires in {} seconds.", authResponse.getExpiresIn());
                        } else {
                            String errorBody = "";
                            try {
                                if (response.hasBody() && response.getBody() != null) { 
                                    errorBody = objectMapper.writeValueAsString(response.getBody());
                                }
                            } catch (Exception e) {
                                logger.error("Failed to parse error response body for auth token: {}", e.getMessage());
                            }
                            throw new RuntimeException("Failed to get Sentinel Hub access token: " + response.getStatusCode() + " - " + errorBody);
                        }
                    } catch (HttpClientErrorException e) { 
                        logger.error("HTTP client error while fetching Sentinel Hub access token: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
                        throw new RuntimeException("HTTP client error while fetching Sentinel Hub access token: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
                    } catch (Exception e) {
                        logger.error("Error while fetching Sentinel Hub access token: {}", e.getMessage(), e);
                        throw new RuntimeException("Error while fetching Sentinel Hub access token: " + e.getMessage(), e);
                    }
                }
            }
        }
        return cachedAccessToken;
    }

    /**
     * Динамически генерирует Evalscript для различных слоев Sentinel Hub.
     * @param layerId Идентификатор слоя (например, "1_TRUE_COLOR", "3_NDVI", "5-MOISTURE-INDEX1").
     * @return Строка с Evalscript.
     * @throws IllegalArgumentException если layerId не поддерживается.
     */
    private String generateEvalscript(String layerId) {
        String evalscript;
        switch (layerId) {
            case "1_TRUE_COLOR":
                evalscript = """
                    //VERSION=3
                    function setup() {
                      return {
                        input: ["B02", "B03", "B04", "dataMask"],
                        output: { bands: 4 }
                      };
                    }
                    function evaluatePixel(sample) {
                      // Scale bands to 0-1 range and apply gamma correction
                      let R = 2.5 * sample.B04;
                      let G = 2.5 * sample.B03;
                      let B = 2.5 * sample.B02;

                      // Clamp values to prevent over/underflow
                      R = Math.min(Math.max(R, 0), 1);
                      G = Math.min(Math.max(G, 0), 1);
                      B = Math.min(Math.max(B, 0), 1);

                      if (sample.dataMask === 1) {
                        return [R, G, B, 1];
                      }
                      return [0, 0, 0, 0]; // Transparent background
                    }
                """;
                break;
            case "2_FALSE_COLOR":
            case "4-FALSE-COLOR-URBAN": 
                // False Color (Urban): NIR-SWIR1-Red (B8-B11-B4)
                // This is often used for urban areas to highlight built-up areas.
                evalscript = """
                    //VERSION=3
                    function setup() {
                      return {
                        input: ["B04", "B08", "B11", "dataMask"], // Red, NIR, SWIR1
                        output: { bands: 4 }
                      };
                    }
                    function evaluatePixel(sample) {
                      // Scale bands
                      let R = 2.5 * sample.B08; // NIR
                      let G = 2.5 * sample.B11; // SWIR1
                      let B = 2.5 * sample.B04; // Red

                      // Clamp values
                      R = Math.min(Math.max(R, 0), 1);
                      G = Math.min(Math.max(G, 0), 1);
                      B = Math.min(Math.max(B, 0), 1);

                      if (sample.dataMask === 1) {
                        return [R, G, B, 1];
                      }
                      return [0, 0, 0, 0]; // Transparent background
                    }
                """;
                break;
            case "3_NDVI":
                evalscript = """
                    //VERSION=3
                    function setup() {
                      return {
                        input: ["B04", "B08", "dataMask"], // B04 (Red), B08 (NIR)
                        output: { bands: 4 } 
                      };
                    }

                    function evaluatePixel(sample) {
                      const val = index(sample.B08, sample.B04); // (NIR - Red) / (NIR + Red)
                      
                      let color = [0, 0, 0, 0]; 
                      if (sample.dataMask === 1) { 
                        if (val < -0.2) color = [0.05, 0.05, 0.05, 1]; // No data, water, snow
                        else if (val < 0) color = [0.75, 0.75, 0.75, 1]; // Bare soil, rocks
                        else if (val < 0.1) color = [0.85, 0.85, 0.6, 1]; // Sparse vegetation, dry soil
                        else if (val < 0.2) color = [0.75, 0.6, 0.25, 1]; // Low density vegetation
                        else if (val < 0.3) color = [0.45, 0.7, 0.3, 1]; // Moderate vegetation
                        else if (val < 0.4) color = [0.2, 0.8, 0.1, 1]; // Healthy vegetation
                        else color = [0, 1, 0, 1]; // Very healthy vegetation (bright green)
                      }
                      return color;
                    }
                """;
                break;
            case "5-MOISTURE-INDEX1": 
                // Moisture Index (MI): (NIR - SWIR1) / (NIR + SWIR1) -> (B8 - B11) / (B8 + B11)
                evalscript = """
                    //VERSION=3
                    function setup() {
                      return {
                        input: ["B08", "B11", "dataMask"], // B08 (NIR), B11 (SWIR1)
                        output: { bands: 4 }
                      };
                    }
                    function evaluatePixel(sample) {
                      const MI = index(sample.B08, sample.B11); 
                      let color = [0, 0, 0, 0];
                      if (sample.dataMask === 1) {
                        if (MI < -0.2) color = [0.9, 0.1, 0.1, 1]; // Very dry (red)
                        else if (MI < 0) color = [0.9, 0.5, 0.1, 1]; // Dry
                        else if (MI < 0.1) color = [0.9, 0.9, 0.1, 1]; // Moderately dry
                        else if (MI < 0.2) color = [0.5, 0.9, 0.1, 1]; // Moist
                        else if (MI < 0.3) color = [0.1, 0.9, 0.1, 1]; // Very moist
                        else color = [0.1, 0.5, 0.9, 1]; // Saturated (blue)
                      }
                      return color;
                    }
                """;
                break;
            case "6-SWIR": // SWIR (Shortwave Infrared) composite, typically B12, B08, B04
                evalscript = """
                    //VERSION=3
                    function setup() {
                      return {
                        input: ["B04", "B08", "B12", "dataMask"], // B04 (Red), B08 (NIR), B12 (SWIR2)
                        output: { bands: 4 }
                      };
                    }
                    function evaluatePixel(sample) {
                      let R = 2.5 * sample.B12; // SWIR2
                      let G = 2.5 * sample.B08; // NIR
                      let B = 2.5 * sample.B04; // Red

                      R = Math.min(Math.max(R, 0), 1);
                      G = Math.min(Math.max(G, 0), 1);
                      B = Math.min(Math.max(B, 0), 1);

                      if (sample.dataMask === 1) {
                        return [R, G, B, 1];
                      }
                      return [0, 0, 0, 0];
                    }
                """;
                break;
            case "7-NDWI": // Normalized Difference Water Index (Green - NIR) / (Green + NIR) -> (B3 - B8) / (B3 + B8)
                evalscript = """
                    //VERSION=3
                    function setup() {
                      return {
                        input: ["B03", "B08", "dataMask"], // B03 (Green), B08 (NIR)
                        output: { bands: 4 }
                      };
                    }
                    function evaluatePixel(sample) {
                      const NDWI = index(sample.B03, sample.B08);
                      let color = [0, 0, 0, 0];
                      if (sample.dataMask === 1) {
                        if (NDWI > 0.5) color = [0.1, 0.1, 0.9, 1]; // Open water (dark blue)
                        else if (NDWI > 0) color = [0.1, 0.5, 0.9, 1]; // Turbid water, moist soil
                        else if (NDWI > -0.2) color = [0.5, 0.9, 0.1, 1]; // Moist vegetation / land
                        else color = [0.9, 0.9, 0.1, 1]; // Dry soil / bare land (yellow)
                      }
                      return color;
                    }
                """;
                break;
            case "8-NDSI": // Normalized Difference Snow Index (Green - SWIR1) / (Green + SWIR1) -> (B3 - B11) / (B3 + B11)
                evalscript = """
                    //VERSION=3
                    function setup() {
                      return {
                        input: ["B03", "B11", "dataMask"], // B03 (Green), B11 (SWIR1)
                        output: { bands: 4 }
                      };
                    }
                    function evaluatePixel(sample) {
                      const NDSI = index(sample.B03, sample.B11);
                      let color = [0, 0, 0, 0];
                      if (sample.dataMask === 1) {
                        if (NDSI > 0.4) color = [0.9, 0.9, 0.9, 1]; // Snow/ice (white)
                        else if (NDSI > 0.1) color = [0.7, 0.7, 0.9, 1]; // Dirty snow, clouds
                        else if (NDSI > -0.1) color = [0.5, 0.5, 0.7, 1]; // Mixed snow/land
                        else color = [0.1, 0.1, 0.1, 1]; // Land/water (black)
                      }
                      return color;
                    }
                """;
                break;
            case "SCENE-CLASSIFICATION": // SCL (Scene Classification Layer)
                evalscript = """
                    //VERSION=3
                    function setup() {
                      return {
                        input: ["SCL", "dataMask"], // Scene Classification Layer
                        output: { bands: 4 }
                      };
                    }
                    function evaluatePixel(sample) {
                      if (sample.dataMask === 0) return [0, 0, 0, 0]; // Transparent background if no data
                      switch (sample.SCL) {
                        case 0: return [0, 0, 0, 0];     // NO_DATA (transparent or black, Sentinel Hub default is black)
                        case 1: return [0, 0, 0, 0];     // SATURATED_OR_DEFECTIVE (black)
                        case 2: return [0.5, 0.5, 0.5, 1]; // DARK_FEATURE_SHADOWS (grey)
                        case 3: return [0.7, 0.4, 0.1, 1]; // CLOUD_SHADOWS (brown)
                        case 4: return [0.1, 0.7, 0.1, 1]; // VEGETATION (green)
                        case 5: return [0.9, 0.9, 0.1, 1]; // NOT_VEGETATED (yellowish-brown)
                        case 6: return [0.1, 0.1, 0.9, 1]; // WATER (blue)
                        case 7: return [0.9, 0.5, 0.9, 1]; // UNCLASSIFIED (magenta)
                        case 8: return [0.9, 0.9, 0.9, 1]; // MEDIUM_PROBABILITY_CLOUDS (light grey)
                        case 9: return [0.9, 0.1, 0.1, 1]; // HIGH_PROBABILITY_CLOUDS (red)
                        case 10: return [0.9, 0.7, 0.5, 1]; // THIN_CIRRUS (light pink/peach)
                        case 11: return [0.1, 0.9, 0.9, 1]; // SNOW_ICE (cyan)
                        default: return [0, 0, 0, 0]; // Should not happen, make transparent
                      }
                    }
                """;
                break;
            default:
                throw new IllegalArgumentException("Unsupported layer ID for masked image: " + layerId);
        }
        return evalscript;
    }

    /**
     * Общий метод для получения маскированного изображения любого поддерживаемого слоя Sentinel Hub.
     * @param geoJson Геометрия полигона в формате GeoJSON (может быть Feature или Polygon).
     * @param layerId Идентификатор слоя для запроса (например, "1_TRUE_COLOR", "3_NDVI").
     * @return Байтовый массив PNG изображения.
     * @throws Exception если произошла ошибка при запросе к Sentinel Hub.
     */
    public byte[] getMaskedImage(String geoJson, String layerId) throws Exception {
        String token = getOrCreateAccessToken();
        JsonNode parsedGeoJson = objectMapper.readTree(geoJson);

        // ✅ ИЗМЕНЕНО: Проверяем, является ли полученный GeoJSON объектом "Feature"
        // Если да, извлекаем его "geometry"
        JsonNode geometryToSend;
        if (parsedGeoJson.isObject() && parsedGeoJson.has("type") && parsedGeoJson.get("type").asText().equals("Feature") && parsedGeoJson.has("geometry")) {
            geometryToSend = parsedGeoJson.get("geometry");
            logger.debug("Извлечена геометрия из GeoJSON Feature для Sentinel Hub.");
        } else {
            // Если это уже объект Polygon/MultiPolygon или другой допустимый тип, используем его напрямую
            geometryToSend = parsedGeoJson;
            logger.debug("GeoJSON отправляется напрямую в Sentinel Hub (не Feature объект).");
        }
        
        String evalscript = generateEvalscript(layerId); // Генерируем evalscript для выбранного слоя

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("input", Map.of(
            "bounds", Map.of("geometry", geometryToSend), // ✅ ИСПОЛЬЗУЕМ geometryToSend
            "data", List.of(Map.of(
                "type", "sentinel-2-l2a",
                "dataFilter", Map.of(
                    "timeRange", Map.of(
                        "from", "2023-01-01T00:00:00Z", 
                        "to", "2024-12-31T23:59:59Z"
                    ),
                    "maxCloudCoverage", 1.0 // ✅ ИЗМЕНЕНО: Увеличено до 100% для теста
                )
            ))
        ));
        requestBody.put("output", Map.of(
            "width", 512, 
            "height", 512, 
            "responses", List.of(Map.of(
                "identifier", "default",
                "format", Map.of("type", "image/png")
            ))
        ));
        requestBody.put("evalscript", evalscript); // Используем сгенерированный evalscript

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.setAccept(Collections.singletonList(MediaType.IMAGE_PNG)); 

        String requestBodyJsonString = objectMapper.writeValueAsString(requestBody);
        logger.debug("Отправка запроса в Sentinel Hub Process API для слоя {}: {}", layerId, requestBodyJsonString); 

        HttpEntity<String> entity = new HttpEntity<>(requestBodyJsonString, headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                processImageUrl, 
                HttpMethod.POST,
                entity,
                byte[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.info("Успешно получено маскированное изображение слоя {} от Sentinel Hub. Размер: {} байт.", layerId, response.getBody().length);
                return response.getBody();
            } else {
                String errorResponseBody = "";
                try {
                    if (response.hasBody() && response.getBody() != null) {
                        // Attempt to read as string if not an image type
                        if (response.getHeaders().getContentType() != null && 
                            !response.getHeaders().getContentType().isCompatibleWith(MediaType.IMAGE_PNG) &&
                            !response.getHeaders().getContentType().isCompatibleWith(MediaType.IMAGE_JPEG)) {
                            errorResponseBody = new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);
                        } else {
                            errorResponseBody = "Бинарное тело ответа (возможно, некорректное изображение или пустое)";
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Не удалось прочитать тело ошибки ответа Sentinel Hub (изображение).", ex);
                    errorResponseBody = "Ошибка при чтении тела ответа: " + ex.getMessage();
                }
                logger.error("Ошибка при получении маскированного изображения слоя {} от Sentinel Hub: Статус {} - {}", layerId, response.getStatusCode(), errorResponseBody);
                throw new RuntimeException("Failed to fetch masked image for layer " + layerId + " from Sentinel Hub: " + response.getStatusCode() + " - " + errorResponseBody);
            }
        } catch (HttpClientErrorException e) {
            // ✅ ИЗМЕНЕНО: Более детальное логирование ошибки HttpClientErrorException
            String errorResponse = e.getResponseBodyAsString();
            logger.error("HTTP client error when fetching masked image for layer {} from Sentinel Hub: Status {} - Response Body: {}", layerId, e.getStatusCode(), errorResponse);
            throw new RuntimeException("HTTP client error when fetching masked image for layer " + layerId + " from Sentinel Hub: " + e.getStatusCode() + " - " + errorResponse, e);
        } catch (Exception e) {
            logger.error("Ошибка при выполнении запроса к Sentinel Hub Process API для слоя {}: {}", layerId, e.getMessage(), e);
            throw new RuntimeException("Error while fetching masked image for layer " + layerId + " from Sentinel Hub: " + e.getMessage(), e);
        }
    }

    // Этот метод теперь используется только для статистики, а не для получения изображения
    public Double getNdvIStatistics(String geoJsonPolygonString, LocalDate fromDate, LocalDate toDate) throws JsonProcessingException {
        String accessToken = getOrCreateAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken); 
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON)); 

        JsonNode parsedGeoJson = objectMapper.readTree(geoJsonPolygonString); 

        // ✅ ИЗМЕНЕНО: Аналогичная проверка для метода статистики
        JsonNode geometryToSend;
        if (parsedGeoJson.isObject() && parsedGeoJson.has("type") && parsedGeoJson.get("type").asText().equals("Feature") && parsedGeoJson.has("geometry")) {
            geometryToSend = parsedGeoJson.get("geometry");
            logger.debug("Извлечена геометрия из GeoJSON Feature для Sentinel Hub статистики.");
        } else {
            geometryToSend = parsedGeoJson;
            logger.debug("GeoJSON отправляется напрямую в Sentinel Hub (не Feature объект) для статистики.");
        }

        Map<String, Object> requestBody = Map.of(
            "input", Map.of(
                "bounds", Map.of("geometry", geometryToSend), // ✅ ИСПОЛЬЗУЕМ geometryToSend
                "data", List.of(Map.of(
                    "type", "sentinel-2-l2a",
                    "dataFilter", Map.of(
                        "timeRange", Map.of(
                            "from", fromDate.atStartOfDay().toString() + "Z",
                            "to", toDate.atTime(23, 59, 59).toString() + "Z"
                        ),
                        "maxCloudCoverage", 0.8 // Также расширим для статистики
                    )
                ))
            ),
            "aggregation", Map.of( 
                "timeRange", Map.of(
                    "from", fromDate.atStartOfDay().toString() + "Z",
                    "to", toDate.atTime(23, 59, 59).toString() + "Z"
                ),
                "aggregationInterval", Map.of("of", "P1D"), 
                "evalscript", """
                    //VERSION=3
                    function setup() {
                        return {
                            input: ["B04", "B08"],
                            output: [{
                                id: "ndvi",
                                bands: 1
                            }]
                        };
                    }
                    function evaluatePixel(sample) {
                        return {
                            ndvi: [index(sample.B08, sample.B04)]
                        };
                    }
                """
            ),
            "calculations", Map.of(
                "default", Map.of("histograms", Map.of("bands", List.of("ndvi")))
            )
        );

        String requestBodyJsonString = objectMapper.writeValueAsString(requestBody);

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBodyJsonString, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                statisticsUrl, 
                HttpMethod.POST,
                requestEntity,
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null && responseBody.containsKey("data")) {
                    List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
                    if (!data.isEmpty()) {
                        Map<String, Object> firstEntry = data.get(0);
                        if (firstEntry.containsKey("outputs")) {
                            Map<String, Object> outputs = (Map<String, Object>) firstEntry.get("outputs");
                            if (outputs.containsKey("ndvi")) {
                                Map<String, Object> ndviOutput = (Map<String, Object>) outputs.get("ndvi");
                                if (ndviOutput.containsKey("bands")) {
                                    Map<String, Object> bands = (Map<String, Object>) ndviOutput.get("bands");
                                    if (bands.containsKey("B0")) {
                                        Map<String, Object> b0 = (Map<String, Object>) bands.get("B0");
                                        if (b0.containsKey("stats")) {
                                            Map<String, Object> stats = (Map<String, Object>) b0.get("stats");
                                            return ((Number) stats.get("mean")).doubleValue(); 
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                throw new RuntimeException("No valid NDVI mean value found in Sentinel Hub statistics response.");
            } else {
                String errorResponseBody = "";
                try {
                    if (response.hasBody() && response.getBody() != null) {
                        errorResponseBody = objectMapper.writeValueAsString(response.getBody());
                    }
                } catch (Exception ex) {
                    logger.error("Не удалось прочитать тело ошибки ответа Sentinel Hub (статистика).", ex);
                    errorResponseBody = "Ошибка при чтении тела ответа: " + ex.getMessage();
                }
                logger.error("Ошибка при получении NDVI данных от Sentinel Hub (статистика): Статус {} - {}", response.getStatusCode(), errorResponseBody);
                throw new RuntimeException("Failed to get Sentinel Hub NDVI data: " + response.getStatusCode() + " - " + errorResponseBody);
            }
        } catch (HttpClientErrorException e) {
            logger.error("HTTP client error when fetching NDVI statistics from Sentinel Hub: Status {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("HTTP client error when fetching NDVI statistics from Sentinel Hub: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            logger.error("Ошибка при выполнении запроса к Sentinel Hub API для статистики: {}", e.getMessage(), e);
            throw new RuntimeException("Error while fetching Sentinel Hub NDVI data: " + e.getMessage(), e);
        }
    }
}
