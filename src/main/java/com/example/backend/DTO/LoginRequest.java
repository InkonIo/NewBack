package com.example.backend.DTO;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
}
