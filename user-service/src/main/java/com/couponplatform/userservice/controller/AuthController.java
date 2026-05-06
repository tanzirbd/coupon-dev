package com.couponplatform.userservice.controller;

import com.couponplatform.userservice.dto.UserDtos.*;
import com.couponplatform.userservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "User registration and login endpoints")
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/auth/register
     * Register a new user account.
     * Core Functionality #1: Authentication Module
     */
    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Creates a new user account and returns a JWT token")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User registered successfully", response));
    }

    /**
     * POST /api/auth/login
     * Authenticate user and return JWT.
     */
    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticates a user and returns a JWT access token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    /**
     * GET /api/auth/health
     * Simple health check (publicly accessible).
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns service health status")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.ok("User Service is running", "OK"));
    }
}
