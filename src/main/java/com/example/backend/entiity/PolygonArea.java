package com.example.backend.entiity;

import jakarta.persistence.*;
import lombok.*;

import org.locationtech.jts.geom.Polygon;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "polygon_areas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolygonArea {

    @Id
    @GeneratedValue
    private UUID id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String geoJson; // <– Сохраняем как строку

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
