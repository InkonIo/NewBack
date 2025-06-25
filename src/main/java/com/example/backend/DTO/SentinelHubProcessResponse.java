package com.example.backend.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SentinelHubProcessResponse {
    private List<DataEntry> data;
    private String status;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataEntry {
        private Interval interval;
        private Map<String, NdviOutput> outputs; // "ndvi" это ключ

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Interval {
            private String from;
            private String to;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NdviOutput {
        // Здесь 'bands' содержит Map, где "B0" - это ключ
        private Map<String, BandOutput> bands; 
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BandOutput {
        private Stats stats;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        private Double min;
        private Double max;
        private Double mean;
        private Double stDev;
        private Integer sampleCount;
        private Integer noDataCount;
    }
}
