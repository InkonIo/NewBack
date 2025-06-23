package com.example.backend.util;

import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeometryUtils {

    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);

    public Polygon convertToPolygon(List<List<List<Double>>> coordinates) {
        List<Coordinate> coords = coordinates.get(0).stream()
                .map(pair -> new Coordinate(pair.get(0), pair.get(1)))
                .toList();

        // замыкаем контур, если не замкнут
        if (!coords.get(0).equals2D(coords.get(coords.size() - 1))) {
            coords = new java.util.ArrayList<>(coords);
            coords.add(coords.get(0));
        }

        return factory.createPolygon(factory.createLinearRing(coords.toArray(new Coordinate[0])));
    }
}
