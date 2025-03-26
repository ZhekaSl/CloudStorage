package ua.zhenya.cloudstorage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.zhenya.cloudstorage.dto.AuthResponse;
import ua.zhenya.cloudstorage.service.impl.AuthService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UserController {
    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser() {
        return ResponseEntity.ok(authService.getCurrentUser());
    }
}
