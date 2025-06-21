package com.example.backend.controller;

import com.example.backend.dto.CodeVerificationRequest;
import com.example.backend.dto.PasswordResetRequest;
import com.example.backend.dto.RecoveryRequest;
import com.example.backend.service.PasswordRecoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/recovery") // ✅ Унифицированный API путь
@CrossOrigin(origins = "*")
public class PasswordRecoveryController {

    @Autowired
    private PasswordRecoveryService passwordRecoveryService;

    @PostMapping("/request")
    public ResponseEntity<String> sendRecoveryCode(@RequestBody RecoveryRequest request) {
        passwordRecoveryService.sendRecoveryCode(request.getEmail());
        return ResponseEntity.ok("Код отправлен на почту");
    }

    @PostMapping("/verify")
    public ResponseEntity<String> verifyCode(@RequestBody CodeVerificationRequest request) {
        boolean isValid = passwordRecoveryService.verifyCode(request.getEmail(), request.getCode());
        if (isValid) {
            return ResponseEntity.ok("Код подтверждён");
        } else {
            return ResponseEntity.badRequest().body("Неверный или просроченный код");
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<String> resetPassword(@RequestBody PasswordResetRequest request) {
        passwordRecoveryService.resetPassword(request.getEmail(), request.getNewPassword());
        return ResponseEntity.ok("Пароль успешно обновлён");
    }
}

