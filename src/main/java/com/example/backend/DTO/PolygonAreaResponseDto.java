// src/main/java/com/example/backend/dto/PolygonAreaResponseDto.java
package com.example.backend.dto;

import java.util.UUID;

import com.example.backend.entiity.PolygonArea;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PolygonAreaResponseDto {
    private UUID id;
    private String name;    // Теперь это поле будет извлекаться из geoJson.properties
    private String geoJson;
    private String crop;    // Теперь это поле будет извлекаться из geoJson.properties

    // ObjectMapper для парсинга JSON
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public PolygonAreaResponseDto(PolygonArea polygonArea) {
        this.id = polygonArea.getId();
        this.geoJson = polygonArea.getGeoJson();

        // Попытка распарсить geoJson и извлечь name и crop из properties
        try {
            JsonNode rootNode = objectMapper.readTree(polygonArea.getGeoJson());
            JsonNode propertiesNode = rootNode.path("properties");

            if (propertiesNode.isObject()) {
                this.name = propertiesNode.path("name").asText(null); // Извлекаем name
                this.crop = propertiesNode.path("crop").asText(null); // Извлекаем crop
            }
        } catch (Exception e) {
            // Обработка ошибки парсинга, если geoJson некорректен
            System.err.println("Error parsing geoJson for PolygonAreaResponseDto: " + e.getMessage());
            this.name = null; // Или какое-то значение по умолчанию
            this.crop = null;
        }
    }
}