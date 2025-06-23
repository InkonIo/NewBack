package com.example.backend.service;

import com.example.backend.entiity.User;
import com.example.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
public class PasswordRecoveryService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder; // Инжектируем PasswordEncoder из SecurityConfig

    // 1. Отправка кода на email
    public void sendRecoveryCode(String email) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            throw new RuntimeException("Пользователь с таким email не найден");
        }

        User user = optionalUser.get();

        String code = String.format("%06d", new Random().nextInt(999999));

        // Эти методы требуют, чтобы в классе User были поля
        // private String resetCode;
        // private LocalDateTime resetCodeExpiry;
        // и соответствующие геттеры/сеттеры (генерируемые Lombok @Data или @Setter)
        user.setResetCode(code); 
        user.setResetCodeExpiry(LocalDateTime.now().plusMinutes(15)); 

        userRepository.save(user);
        emailService.sendResetCode(user.getEmail(), code);
    }

    // 2. Проверка введённого кода
    public boolean verifyCode(String email, String code) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            return false;
        }

        User user = optionalUser.get();

        // Эти методы требуют, чтобы в классе User были поля
        // private String resetCode;
        // private LocalDateTime resetCodeExpiry;
        // и соответствующие геттеры/сеттеры (генерируемые Lombok @Data или @Getter)
        return user.getResetCode() != null &&
               user.getResetCodeExpiry() != null &&
               user.getResetCode().equals(code) &&
               user.getResetCodeExpiry().isAfter(LocalDateTime.now());
    }

    // 3. Сброс пароля
    public void resetPassword(String email, String newPassword) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            throw new RuntimeException("Пользователь не найден");
        }

        User user = optionalUser.get();

        String hashedPassword = passwordEncoder.encode(newPassword);
        user.setPasswordHash(hashedPassword);

        // Эти методы требуют, чтобы в классе User были поля
        // private String resetCode;
        // private LocalDateTime resetCodeExpiry;
        // и соответствующие геттеры/сеттеры (генерируемые Lombok @Data или @Setter)
        user.setResetCode(null);
        user.setResetCodeExpiry(null);

        userRepository.save(user);
    }
}
