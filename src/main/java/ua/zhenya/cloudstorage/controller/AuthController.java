package ua.zhenya.cloudstorage.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.zhenya.cloudstorage.dto.AuthRequest;
import ua.zhenya.cloudstorage.dto.AuthResponse;
import ua.zhenya.cloudstorage.service.impl.AuthService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<AuthResponse> signUp(@Valid @RequestBody AuthRequest authRequest, HttpServletRequest request) {
        AuthResponse authResponse = authService.signUp(authRequest, request);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/sign-in")
    public ResponseEntity<AuthResponse> signIn(@Valid @RequestBody AuthRequest authRequest, HttpServletRequest request) {
        AuthResponse authResponse = authService.signIn(authRequest, request);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(HttpServletRequest request, HttpServletResponse response) {
        authService.signOut(request, response);
        return ResponseEntity.ok().build();
    }
}
