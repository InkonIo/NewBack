// src/main/java/com/example/backend/dto/PolygonRequestDto.java
package com.example.backend.dto;

import java.util.UUID; // Не забудьте импортировать UUID, если вы используете его как ID

import lombok.Data;

@Data
public class PolygonRequestDto {
    private UUID id; // Добавляем ID для использования в PUT запросах, когда ID передается в теле
    private String geoJson; // Это поле теперь будет содержать GeoJSON Feature с name и crop в properties
}