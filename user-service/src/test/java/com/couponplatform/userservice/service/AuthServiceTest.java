package com.couponplatform.userservice.service;

import com.couponplatform.userservice.dto.UserDtos.*;
import com.couponplatform.userservice.model.User;
import com.couponplatform.userservice.repository.UserRepository;
import com.couponplatform.userservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 * Core Functionality #1: Authentication Module (FR: User register, login, JWT)
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks private AuthService authService;

    private RegisterRequest validRegisterRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        validRegisterRequest = RegisterRequest.builder()
                .username("testuser")
                .email("test@example.com")
                .password("SecurePass123")
                .fullName("Test User")
                .role(User.Role.USER)
                .build();

        savedUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("$2a$12$hashedPassword")
                .fullName("Test User")
                .role(User.Role.USER)
                .build();
    }

    // ──────────────── Register Tests ─────────────────────────────────────────

    @Test
    @DisplayName("Register - success returns JWT and user data")
    void register_success_returnsAuthResponse() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123")).thenReturn("$2a$12$hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(savedUser)).thenReturn("test.jwt.token");
        when(jwtService.getExpirationSeconds()).thenReturn(86400L);

        AuthResponse response = authService.register(validRegisterRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("test.jwt.token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(86400L);
        assertThat(response.getUser().getUsername()).isEqualTo("testuser");
        assertThat(response.getUser().getRole()).isEqualTo(User.Role.USER);

        verify(passwordEncoder).encode("SecurePass123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Register - duplicate username throws IllegalArgumentException")
    void register_duplicateUsername_throwsException() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(validRegisterRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Register - duplicate email throws IllegalArgumentException")
    void register_duplicateEmail_throwsException() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(validRegisterRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("Register - defaults role to USER when not specified")
    void register_noRole_defaultsToUser() {
        validRegisterRequest.setRole(null);

        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(savedUser);
        when(jwtService.generateToken(any())).thenReturn("token");
        when(jwtService.getExpirationSeconds()).thenReturn(86400L);

        authService.register(validRegisterRequest);

        verify(userRepository).save(argThat(user -> user.getRole() == User.Role.USER));
    }

    // ──────────────── Login Tests ─────────────────────────────────────────────

    @Test
    @DisplayName("Login - success returns JWT")
    void login_success_returnsAuthResponse() {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("testuser").password("SecurePass123").build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(savedUser));
        when(jwtService.generateToken(savedUser)).thenReturn("login.jwt.token");
        when(jwtService.getExpirationSeconds()).thenReturn(86400L);

        AuthResponse response = authService.login(loginRequest);

        assertThat(response.getAccessToken()).isEqualTo("login.jwt.token");
        assertThat(response.getUser().getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Login - wrong credentials throws BadCredentialsException")
    void login_wrongPassword_throwsBadCredentials() {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("testuser").password("WrongPass").build();

        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);
    }
}
