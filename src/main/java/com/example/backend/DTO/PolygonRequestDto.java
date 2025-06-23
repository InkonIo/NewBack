package com.example.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class PolygonRequestDto {
    private String name;
    private String geoJson; // ← просто строка из Leaflet
}

