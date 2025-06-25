// src/main/java/com/example/backend/dto/PolygonRequestDto.java
package com.example.backend.dto;

import lombok.Data; // Убедитесь, что Lombok импортирован

@Data // Эта аннотация автоматически генерирует геттеры и сеттеры для всех полей
public class PolygonRequestDto {
    private String name;    // Должен иметь getName() и setName()
    private String geoJson; // Должен иметь getGeoJson() и setGeoJson()
    private String crop;    // Должен иметь getCrop() и setCrop()
}
