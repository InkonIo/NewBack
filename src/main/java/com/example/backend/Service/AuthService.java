package com.example.backend.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.backend.JWT.JwtService;
import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.dto.RegisterRequest;
import com.example.backend.entiity.User; // Ваш кастомный User entity
import com.example.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    // Используем PasswordEncoder из SecurityConfig, чтобы он был Bean-ом
    // @Autowired
    // private PasswordEncoder passwordEncoder; // Если вы используете @Autowired
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(); // Или так, если не инжектируется

    public String register(RegisterRequest request) {
        try {
            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                return "User with this email already exists";
            }

            User user = User.builder()
                    // .username(request.getUsername()) // УБРАНО: поле 'username' удалено из User entity
                    .email(request.getEmail()) // Email теперь является основным идентификатором
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .build();

            userRepository.save(user);
            return "User registered successfully";
        } catch (Exception e) {
            e.printStackTrace(); // Покажи в логе причину
            return "Registration failed: " + e.getMessage();
        }
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        // ✅ Генерация JWT-токена
        // Поскольку User теперь реализует UserDetails, можно передать его напрямую
        // или использовать user.getEmail() который является getUsername()
        String token = jwtService.generateToken(user.getEmail()); 

        return new LoginResponse("Login successful", token);
    }
}
