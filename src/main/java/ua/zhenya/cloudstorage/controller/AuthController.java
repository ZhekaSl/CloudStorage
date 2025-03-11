package ua.zhenya.cloudstorage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.zhenya.cloudstorage.dto.UserCreateRequest;
import ua.zhenya.cloudstorage.dto.UserResponse;
import ua.zhenya.cloudstorage.service.UserService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;

    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UserResponse> signUp(@RequestBody UserCreateRequest userCreateRequest) {
        UserResponse userResponse = userService.createUser(userCreateRequest);
        return ResponseEntity.ok(userResponse);
    }
}
