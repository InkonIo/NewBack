package com.example.backend.service;

import com.example.backend.entiity.PolygonArea; // Обновлен импорт к PolygonArea
import com.example.backend.entiity.User;       // Импорт User
import com.example.backend.repository.PolygonAreaRepository; // Обновлен импорт к PolygonAreaRepository
import com.example.backend.repository.UserRepository;     // Импорт UserRepository
import com.fasterxml.jackson.databind.ObjectMapper; // Для возможного парсинга GeoJSON на бэкенде
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PolygonAreaService { // Переименован, чтобы соответствовать PolygonAreaRepository

    private final PolygonAreaRepository polygonAreaRepository;
    private final UserRepository userRepository; // Для получения объекта User
    private final ObjectMapper objectMapper; // Для работы с JSON (если потребуется парсинг geoJson на бэкенде)

    public PolygonAreaService(PolygonAreaRepository polygonAreaRepository, UserRepository userRepository, ObjectMapper objectMapper) {
        this.polygonAreaRepository = polygonAreaRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    // Вспомогательный метод для получения ID текущего аутентифицированного пользователя
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof User userDetails) {
            return userDetails.getId();
        }
        // В реальном приложении здесь можно выбросить более специфичное исключение,
        // которое будет обработано @ControllerAdvice для возврата 401/403.
        throw new IllegalStateException("User not authenticated or user ID not available in security context.");
    }

    /**
     * Получает PolygonArea по его ID, убеждаясь, что он принадлежит текущему аутентифицированному пользователю.
     * @param id UUID полигона.
     * @return Optional<PolygonArea>, если полигон найден и принадлежит пользователю.
     * @throws IllegalArgumentException если полигон не найден или не принадлежит текущему пользователю.
     */
    public Optional<PolygonArea> getPolygonByIdForCurrentUser(UUID id) {
        Long userId = getCurrentUserId();
        Optional<PolygonArea> optionalPolygon = polygonAreaRepository.findById(id);

        if (optionalPolygon.isEmpty()) {
            throw new IllegalArgumentException("Polygon with ID " + id + " not found.");
        }

        PolygonArea polygon = optionalPolygon.get();
        if (!polygon.getUser().getId().equals(userId)) {
            // Если полигон найден, но принадлежит другому пользователю, это попытка несанкционированного доступа.
            throw new SecurityException("Access denied. Polygon does not belong to the current user.");
        }
        return optionalPolygon;
    }

    /**
     * Получает все PolygonArea, принадлежащие текущему аутентифицированному пользователю.
     * @return Список PolygonArea.
     */
    public List<PolygonArea> getAllPolygonsForCurrentUser() {
        Long userId = getCurrentUserId();
        return polygonAreaRepository.findAllByUserId(userId);
    }

    /**
     * Сохраняет новый PolygonArea или обновляет существующий.
     * Автоматически связывает полигон с текущим аутентифицированным пользователем.
     * @param polygonArea Объект PolygonArea для сохранения. Его ID может быть null для новых полигонов.
     * Поле `geoJson` должно содержать полную GeoJSON Feature строку,
     * включая name и crop в properties.
     * @return Сохраненный PolygonArea.
     * @throws IllegalStateException если аутентифицированный пользователь не найден в БД.
     * @throws SecurityException если попытка обновить полигон, который не принадлежит текущему пользователю.
     */
    @Transactional
    public PolygonArea savePolygon(PolygonArea polygonArea) {
        Long userId = getCurrentUserId();
        User currentUser = userRepository.findById(userId)
                               .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));
        
        // Устанавливаем пользователя для полигона
        polygonArea.setUser(currentUser); 

        // Если это новый полигон, генерируем UUID
        if (polygonArea.getId() == null) {
            polygonArea.setId(UUID.randomUUID());
        } else {
            // Если это обновление существующего полигона, проверяем права доступа
            Optional<PolygonArea> existingPolygon = polygonAreaRepository.findById(polygonArea.getId());
            if (existingPolygon.isPresent() && !existingPolygon.get().getUser().getId().equals(userId)) {
                throw new SecurityException("Access denied. User is not authorized to update this polygon.");
            }
        }
        return polygonAreaRepository.save(polygonArea);
    }

    /**
     * Удаляет PolygonArea по его ID.
     * Проверяет, что полигон принадлежит текущему аутентифицированному пользователю.
     * @param id UUID полигона для удаления.
     * @throws IllegalArgumentException если полигон не найден.
     * @throws SecurityException если полигон не принадлежит текущему пользователю.
     */
    public void deletePolygon(UUID id) {
        Long userId = getCurrentUserId();
        Optional<PolygonArea> optionalPolygon = polygonAreaRepository.findById(id);

        if (optionalPolygon.isEmpty()) {
            throw new IllegalArgumentException("Polygon with ID " + id + " not found.");
        }

        PolygonArea polygon = optionalPolygon.get();
        if (!polygon.getUser().getId().equals(userId)) {
            throw new SecurityException("Access denied. User is not authorized to delete this polygon.");
        }

        polygonAreaRepository.delete(polygon);
    }

    /**
     * Удаляет все полигоны, принадлежащие текущему аутентифицированному пользователю.
     */
    @Transactional
    public void deleteAllPolygonsForCurrentUser() {
        Long userId = getCurrentUserId();
        List<PolygonArea> userPolygons = polygonAreaRepository.findAllByUserId(userId);
        if (!userPolygons.isEmpty()) {
            polygonAreaRepository.deleteAll(userPolygons);
        }
    }
}