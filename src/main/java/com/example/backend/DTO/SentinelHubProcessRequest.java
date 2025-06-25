package com.example.backend.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode; // Импортировано для форматирования даты

public class SentinelHubProcessRequest {

    // Default constructor for Jackson
    public SentinelHubProcessRequest() {
    }

    /**
     * Constructs the full JSON request body for the Sentinel Hub Statistical API.
     * This method directly builds and returns a JsonNode, which will then be serialized to a string.
     *
     * @param geoJsonPolygon The GeoJSON string representing the polygon geometry.
     * @param fromDate The start date for the data query.
     * @param toDate The end date for the data query.
     * @return A JsonNode representing the complete request body.
     */
    public JsonNode getRequestBody(String geoJsonPolygon, LocalDate fromDate, LocalDate toDate) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode rootNode = mapper.createObjectNode();

        // Input section
        ObjectNode inputNode = mapper.createObjectNode();
        ObjectNode boundsNode = mapper.createObjectNode();
        try {
            JsonNode geometryNode = mapper.readTree(geoJsonPolygon);
            boundsNode.set("geometry", geometryNode);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse geoJsonPolygon: " + e.getMessage(), e);
        }
        
        ArrayNode dataArray = mapper.createArrayNode();
        ObjectNode dataNode = mapper.createObjectNode();
        // Изменено на "sentinel-2-l2a"
        dataNode.set("type", mapper.getNodeFactory().textNode("sentinel-2-l2a")); 
        
        // DataFilter with timeRange and mosaickingOrder
        ObjectNode dataFilterNode = mapper.createObjectNode();
        ObjectNode timeRangeNode = mapper.createObjectNode();
        timeRangeNode.set("from", mapper.getNodeFactory().textNode(fromDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + "T00:00:00Z"));
        timeRangeNode.set("to", mapper.getNodeFactory().textNode(toDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + "T23:59:59Z"));
        dataFilterNode.set("timeRange", timeRangeNode);
        dataFilterNode.set("mosaickingOrder", mapper.getNodeFactory().textNode("leastCC"));
        
        dataNode.set("dataFilter", dataFilterNode);
        dataArray.add(dataNode);

        inputNode.set("bounds", boundsNode);
        inputNode.set("data", dataArray);
        rootNode.set("input", inputNode);

        // Aggregation section
        ObjectNode aggregationNode = mapper.createObjectNode();
        ObjectNode aggTimeRange = mapper.createObjectNode();
        aggTimeRange.set("from", mapper.getNodeFactory().textNode(fromDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + "T00:00:00Z"));
        aggTimeRange.set("to", mapper.getNodeFactory().textNode(toDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + "T23:59:59Z"));
        aggregationNode.set("timeRange", aggTimeRange);

        ObjectNode aggInterval = mapper.createObjectNode();
        aggInterval.set("of", mapper.getNodeFactory().textNode("P1D"));
        aggregationNode.set("aggregationInterval", aggInterval);
        
        // Evalscript - обновлен, чтобы полностью соответствовать Postman
        String evalscriptContent = """
            //VERSION=3
            function setup(){return {input:[{bands:["B04","B08","dataMask"]}], output:[{id:"ndvi",bands:1},{id:"dataMask",bands:1}]}}
            function evaluatePixel(s){let ndvi = (s.B08 - s.B04) / (s.B08 + s.B04); return {ndvi:[ndvi],dataMask:[s.dataMask]};}
        """;
        aggregationNode.set("evalscript", mapper.getNodeFactory().textNode(evalscriptContent));

        // Добавлены resx и resy, как в Postman запросе
        aggregationNode.put("resx", 10);
        aggregationNode.put("resy", 10);

        rootNode.set("aggregation", aggregationNode);

        // Секция "calculations" УДАЛЕНА, так как ее нет в рабочем Postman запросе
        // и она не нужна для получения статистики таким образом.
        // Вместо этого evalscript напрямую возвращает нужные бэнды.

        return rootNode;
    }
}
