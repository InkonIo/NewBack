// src/main/java/com/example/backend/entiity/PolygonArea.java
package com.example.backend.entiity;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column; 
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "polygon_areas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolygonArea {

    @Id
    private UUID id;

    // ВОЗВРАЩЕНЫ поля name и crop, чтобы соответствовать PolygonAreaController
    private String name;

    @Column(columnDefinition = "TEXT") 
    private String crop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore 
    private User user; 

    @Column(columnDefinition = "TEXT")
    private String geoJson; // Это поле теперь будет содержать ТОЛЬКО строку GeoJSON ГЕОМЕТРИИ
}
