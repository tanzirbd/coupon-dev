package com.couponplatform.userservice.controller;

import com.couponplatform.userservice.dto.UserDtos.*;
import com.couponplatform.userservice.model.User;
import com.couponplatform.userservice.repository.UserRepository;
import com.couponplatform.userservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * User profile management endpoints (authenticated).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Profile management — requires authentication")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserRepository userRepository;
    private final AuthService authService;

    /**
     * GET /api/users/me — current user's profile.
     */
    @GetMapping("/me")
    @Operation(summary = "Get my profile")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .map(u -> ResponseEntity.ok(ApiResponse.ok("Profile loaded", authService.toUserResponse(u))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("User not found")));
    }

    /**
     * GET /api/users/admin/all — admin: list all users (paginated).
     */
    @GetMapping("/admin/all")
    @Operation(summary = "List all users (admin only)")
    public ResponseEntity<ApiResponse<?>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<User> users = userRepository.findAll(PageRequest.of(page, size));
        var result = users.getContent().stream()
                .map(authService::toUserResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Users loaded", result));
    }
}
