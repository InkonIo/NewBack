package com.example.backend.entiity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users") // Предполагается, что ваша таблица пользователей называется 'users'
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// --- ВАЖНОЕ ИЗМЕНЕНИЕ: Реализация интерфейса UserDetails ---
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Или ваша стратегия генерации ID
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    // --- НОВЫЕ ПОЛЯ ДЛЯ ВОССТАНОВЛЕНИЯ ПАРОЛЯ ---
    private String resetCode;
    private LocalDateTime resetCodeExpiry;

    // --- Методы интерфейса UserDetails ---
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return this.passwordHash;
    }

    @Override
    public String getUsername() {
        return this.email; // Используем email как имя пользователя для Spring Security
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
