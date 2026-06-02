package com.couponplatform.apigateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JwtUtil.
 * Core Functionality: JWT token validation in API Gateway
 *
 * Tests JWT parsing, validation, and claim extraction.
 */
@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    @InjectMocks private JwtUtil jwtUtil;

    private String secret;
    private String validToken;
    private String expiredToken;
    private String invalidToken;

    @BeforeEach
    void setUp() {
        // Use a 256-bit key for HMAC-SHA256
        secret = "my-super-secret-key-that-is-long-enough-for-256-bits";
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);

        // Generate valid token
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        validToken = Jwts.builder()
                .subject("testuser")
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hour from now
                .signWith(key)
                .compact();

        // Generate expired token
        expiredToken = Jwts.builder()
                .subject("expireduser")
                .claim("role", "ADMIN")
                .issuedAt(new Date(System.currentTimeMillis() - 7200000))
                .expiration(new Date(System.currentTimeMillis() - 3600000)) // 1 hour ago
                .signWith(key)
                .compact();

        invalidToken = "invalid.token.format";
    }

    // ──────────────── Token Validation Tests ────────────────────────────────

    @Test
    @DisplayName("IsTokenValid - valid token returns true")
    void isTokenValid_validToken_returnsTrue() {
        assertThat(jwtUtil.isTokenValid(validToken)).isTrue();
    }

    @Test
    @DisplayName("IsTokenValid - expired token returns false")
    void isTokenValid_expiredToken_returnsFalse() {
        assertThat(jwtUtil.isTokenValid(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("IsTokenValid - malformed token returns false")
    void isTokenValid_malformedToken_returnsFalse() {
        assertThat(jwtUtil.isTokenValid(invalidToken)).isFalse();
    }

    @Test
    @DisplayName("IsTokenValid - null token returns false")
    void isTokenValid_nullToken_returnsFalse() {
        assertThat(jwtUtil.isTokenValid(null)).isFalse();
    }

    @Test
    @DisplayName("IsTokenValid - empty string returns false")
    void isTokenValid_emptyString_returnsFalse() {
        assertThat(jwtUtil.isTokenValid("")).isFalse();
    }

    // ──────────────── Claim Extraction Tests ───────────────────────────────

    @Test
    @DisplayName("ExtractUsername - valid token returns correct subject")
    void extractUsername_validToken_returnsCorrectUsername() {
        String username = jwtUtil.extractUsername(validToken);

        assertThat(username).isEqualTo("testuser");
    }

    @Test
    @DisplayName("ExtractRole - valid token returns correct role")
    void extractRole_validToken_returnsCorrectRole() {
        String role = jwtUtil.extractRole(validToken);

        assertThat(role).isEqualTo("USER");
    }


    // ──────────────── Claims Parsing Tests ────────────────────────────────

    @Test
    @DisplayName("ExtractAllClaims - returns parsed claims object")
    void extractAllClaims_validToken_returnsClaimsObject() {
        Claims claims = jwtUtil.extractAllClaims(validToken);

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("testuser");
        assertThat(claims.get("role")).isEqualTo("USER");
    }

    @Test
    @DisplayName("ExtractAllClaims - contains issued-at and expiration")
    void extractAllClaims_validToken_containsTimestamps() {
        Claims claims = jwtUtil.extractAllClaims(validToken);

        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    @DisplayName("ExtractAllClaims - invalid token throws exception")
    void extractAllClaims_invalidToken_throwsException() {
        assertThatThrownBy(() -> jwtUtil.extractAllClaims(invalidToken))
                .isInstanceOf(Exception.class);
    }

    // ──────────────── Token Composition Tests ──────────────────────────────

    @Test
    @DisplayName("Extracted role claim matches token construction")
    void extractRole_matchesConstructedClaim() {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("roletest")
                .claim("role", "PREMIUM_USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();

        String extractedRole = jwtUtil.extractRole(token);

        assertThat(extractedRole).isEqualTo("PREMIUM_USER");
    }

    @Test
    @DisplayName("Extracted username matches token construction")
    void extractUsername_matchesConstructedSubject() {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("alice@example.com")
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();

        String extractedUsername = jwtUtil.extractUsername(token);

        assertThat(extractedUsername).isEqualTo("alice@example.com");
    }

    // ──────────────── Multiple Token Handling Tests ───────────────────────

    @Test
    @DisplayName("Can process multiple different valid tokens")
    void processMultipleTokens_allHandledIndependently() {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        String token1 = Jwts.builder()
                .subject("user1")
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();

        String token2 = Jwts.builder()
                .subject("user2")
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();

        assertThat(jwtUtil.extractUsername(token1)).isEqualTo("user1");
        assertThat(jwtUtil.extractRole(token1)).isEqualTo("USER");
        assertThat(jwtUtil.extractUsername(token2)).isEqualTo("user2");
        assertThat(jwtUtil.extractRole(token2)).isEqualTo("ADMIN");
    }
}

