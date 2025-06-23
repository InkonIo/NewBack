package com.example.backend.entiity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

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

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String geoJson;
}
