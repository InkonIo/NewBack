// src/main/java/com/example/backend/dto/PolygonAreaResponseDto.java
package com.example.backend.dto;

import java.util.UUID; 

import org.slf4j.Logger; // Импортируйте вашу сущность
import org.slf4j.LoggerFactory;

import com.example.backend.entiity.PolygonArea;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper; // Добавлено для логирования ошибок парсинга

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor 
public class PolygonAreaResponseDto {
    private UUID id;
    private String name;    // Теперь это поле будет извлекаться из geoJson.properties
    private String geoJson; // Поле 'geoJson' берется напрямую из сущности (это строка GeoJSON геометрии)
    private String crop;    // Теперь это поле будет извлекаться из geoJson.properties

    // ObjectMapper для парсинга JSON
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(PolygonAreaResponseDto.class); // Добавлено логирование

    // Конструктор, который преобразует сущность PolygonArea в DTO
    public PolygonAreaResponseDto(PolygonArea polygonArea) {
        this.id = polygonArea.getId();
        // this.name = polygonArea.getName();    // УДАЛЕНО: имя больше не прямое поле сущности
        this.geoJson = polygonArea.getGeoJson();
        // this.crop = polygonArea.getCrop();    // УДАЛЕНО: культура больше не прямое поле сущности

        // Попытка распарсить geoJson и извлечь name и crop из properties
        try {
            // Ожидаем, что geoJson в PolygonArea содержит ПОЛНЫЙ GeoJSON Feature
            // включая свойства, как указано в вашей сущности PolygonArea:
            // "Теперь это поле будет содержать GeoJSON Feature с name и crop в properties"
            JsonNode rootNode = objectMapper.readTree(polygonArea.getGeoJson());
            JsonNode propertiesNode = rootNode.path("properties"); // Получаем узел "properties"

            if (propertiesNode.isObject()) {
                this.name = propertiesNode.path("name").asText(null); // Извлекаем name из свойств
                this.crop = propertiesNode.path("crop").asText(null); // Извлекаем crop из свойств
            } else {
                log.warn("GeoJSON for polygon ID {} does not contain an object 'properties' node or it's empty. Cannot extract name/crop.", polygonArea.getId());
                // Fallback для имени и культуры, если свойства не найдены
                this.name = null; 
                this.crop = null;
            }
            
            // Если вы хотите, чтобы geoJson в DTO был только геометрией, а не полным Feature,
            // то здесь нужно извлечь только геометрию:
            // JsonNode geometryNode = rootNode.path("geometry");
            // if (geometryNode.isObject()) {
            //     this.geoJson = objectMapper.writeValueAsString(geometryNode);
            // } else {
            //     log.error("GeoJSON for polygon ID {} does not contain a valid 'geometry' node.", polygonArea.getId());
            //     this.geoJson = polygonArea.getGeoJson(); // Сохраняем как есть в случае ошибки
            // }

            // Исправлено: если PolygonArea.geoJson хранит ТОЛЬКО геометрию,
            // а name/crop хранятся в отдельных полях PolygonArea (как в предыдущих версиях),
            // то этот парсинг не нужен, и тогда верна предыдущая версия DTO.
            // Но исходя из предоставленной вами PolygonArea, где name/crop удалены
            // и "geoJson" будет содержать Feature, этот парсинг необходим.

            // Если ваш бэкенд PolygonArea.java НЕ имеет name и crop как отдельные поля
            // (как вы показали в последнем сообщении), то этот DTO должен парсить их из geoJson.
            // В противном случае, если PolygonArea.java БУДЕТ иметь name и crop, 
            // то DTO должен просто брать их напрямую.
            // Я следую вашей последней версии PolygonArea, где name и crop удалены.

        } catch (Exception e) {
            log.error("Error parsing geoJson for PolygonAreaResponseDto (ID: {}): {}", polygonArea.getId(), e.getMessage(), e);
            this.name = null; // Или какое-то значение по умолчанию
            this.crop = null;
            // this.geoJson = polygonArea.getGeoJson(); // Оставляем как есть, если не удалось распарсить
        }
    }
}
