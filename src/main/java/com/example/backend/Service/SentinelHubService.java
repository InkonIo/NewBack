package com.example.backend.service;

import java.time.LocalDate;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.example.backend.dto.SentinelHubAuthResponse;
import com.example.backend.dto.SentinelHubProcessRequest;
import com.example.backend.dto.SentinelHubProcessResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SentinelHubService {

    @Value("${sentinelhub.auth.url}")
    private String authUrl;

    // ОБНОВЛЕНО: Используем 'statistics' в URL. Убедитесь, что в application.properties
    // у вас установлено sentinelhub.process.url=https://services.sentinel-hub.com/api/v1/statistics
    @Value("${sentinelhub.process.url}") 
    private String processUrl;

    @Value("${sentinelhub.client.id}")
    private String clientId;

    @Value("${sentinelhub.client.secret}")
    private String clientSecret;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper; 

    // Поля для кэширования токена
    private volatile String cachedAccessToken;
    private volatile long tokenExpiryTimeMillis;
    private final Object tokenRefreshLock = new Object();

    public SentinelHubService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Получает или обновляет токен доступа от Sentinel Hub.
     * Если токен отсутствует или истек, запрашивает новый.
     * Обеспечивает потокобезопасность при обновлении токена.
     * @return Действительный токен доступа Sentinel Hub.
     * @throws RuntimeException если не удалось получить токен.
     */
    private String getOrCreateAccessToken() {
        if (cachedAccessToken == null || System.currentTimeMillis() >= tokenExpiryTimeMillis - (5 * 60 * 1000)) { 
            synchronized (tokenRefreshLock) {
                if (cachedAccessToken == null || System.currentTimeMillis() >= tokenExpiryTimeMillis - (5 * 60 * 1000)) {
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
                            tokenExpiryTimeMillis = System.currentTimeMillis() + (authResponse.getExpiresIn() * 1000);
                            System.out.println("Sentinel Hub access token refreshed. Expires in " + authResponse.getExpiresIn() + " seconds.");
                        } else {
                            throw new RuntimeException("Failed to get Sentinel Hub access token: " + response.getStatusCode() + ". Response: " + response.getBody());
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Error while fetching Sentinel Hub access token: " + e.getMessage(), e);
                    }
                }
            }
        }
        return cachedAccessToken;
    }

    // Метод для получения среднего значения NDVI для полигона
    public Double getNdvIStatistics(String geoJsonPolygonString, LocalDate fromDate, LocalDate toDate) {
        String accessToken = getOrCreateAccessToken(); 

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON)); 

        SentinelHubProcessRequest requestBuilder = new SentinelHubProcessRequest();
        JsonNode requestBodyJson = requestBuilder.getRequestBody(geoJsonPolygonString, fromDate, toDate);

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBodyJson.toString(), headers); 

        try {
            ResponseEntity<SentinelHubProcessResponse> response = restTemplate.exchange(
                processUrl,
                HttpMethod.POST,
                requestEntity,
                SentinelHubProcessResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                SentinelHubProcessResponse processResponse = response.getBody();
                
                // ОБНОВЛЕННАЯ ЛОГИКА ПАРСИНГА СООТВЕТСТВУЕТ ОТВЕТУ POSTMAN
                if (processResponse.getData() != null && !processResponse.getData().isEmpty()) {
                    // Итерируем по всем записям данных, чтобы найти валидное NDVI
                    for (SentinelHubProcessResponse.DataEntry dataEntry : processResponse.getData()) {
                        if (dataEntry != null && 
                            dataEntry.getOutputs() != null && 
                            dataEntry.getOutputs().containsKey("ndvi") && // Ключ "ndvi"
                            dataEntry.getOutputs().get("ndvi").getBands() != null &&
                            dataEntry.getOutputs().get("ndvi").getBands().containsKey("B0") && // Ключ "B0"
                            dataEntry.getOutputs().get("ndvi").getBands().get("B0").getStats() != null) {
                            
                            Double meanNdvI = dataEntry.getOutputs().get("ndvi").getBands().get("B0").getStats().getMean();
                            if (meanNdvI != null) {
                                return meanNdvI; // Возвращаем первое найденное среднее значение NDVI
                            }
                        }
                    }
                }
                throw new RuntimeException("No valid NDVI mean value found in Sentinel Hub response.");
            } else {
                String errorBody = response.getBody() != null ? response.getBody().toString() : response.getStatusCode().toString(); 
                throw new RuntimeException("Failed to get Sentinel Hub NDVI data: " + response.getStatusCode() + ". Response: " + errorBody);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while fetching Sentinel Hub NDVI data: " + e.getMessage(), e);
        }
    }
}
