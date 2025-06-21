package com.example.backend.dto;

import lombok.Data;

@Data
public class CodeVerificationRequest {
    private String email;
    private String code;
}
