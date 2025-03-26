package ua.zhenya.cloudstorage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.zhenya.cloudstorage.dto.AuthRequest;
import ua.zhenya.cloudstorage.dto.AuthResponse;
import ua.zhenya.cloudstorage.service.impl.AuthServiceImpl;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "API for user Sign-Up, Sign-In, and Sign-Out")
public class AuthController {
    private final AuthServiceImpl authServiceImpl;

    @Operation(summary = "Register a new user", description = "Registers a new user with username and password, and logs them in immediately, returning the username.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User registered and logged in successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data (e.g., blank username/password)",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - User is already authenticated",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Conflict - Username already exists",
                    content = @Content)
    })
    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<AuthResponse> signUp(@Valid @RequestBody AuthRequest authRequest, HttpServletRequest request) {
        AuthResponse authResponse = authServiceImpl.signUp(authRequest, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
    }

    @Operation(summary = "Authenticate a user", description = "Logs in an existing user with username and password, returning the username.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User authenticated successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data (e.g., blank username/password)",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid username or password",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - User is already authenticated",
                    content = @Content)
    })
    @PostMapping("/sign-in")
    public ResponseEntity<AuthResponse> signIn(@Valid @RequestBody AuthRequest authRequest, HttpServletRequest request) {
        AuthResponse authResponse = authServiceImpl.signIn(authRequest, request);
        return ResponseEntity.ok(authResponse);
    }

    @Operation(summary = "Log out user", description = "Logs out the currently authenticated user by invalidating their session/token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User logged out successfully",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized - No valid session/token found to log out", // User needs to be authenticated to log out
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error during logout",
                    content = @Content)
    })
    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(HttpServletRequest request, HttpServletResponse response) {
        authServiceImpl.signOut(request, response);
        return ResponseEntity.noContent().build();
    }
}
