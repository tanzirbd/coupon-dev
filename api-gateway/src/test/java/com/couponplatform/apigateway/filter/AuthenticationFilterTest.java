package com.couponplatform.apigateway.filter;

import com.couponplatform.apigateway.config.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationFilter.
 * Core Functionality: JWT authentication in API Gateway
 *
 * Tests request filtering, token validation, and header manipulation.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private ServerWebExchange exchange;
    @Mock private ServerHttpRequest request;
    @Mock private ServerHttpResponse response;
    @Mock private org.springframework.cloud.gateway.filter.GatewayFilterChain chain;

    @InjectMocks private AuthenticationFilter authenticationFilter;

    private GatewayFilter gatewayFilter;
    private AuthenticationFilter.Config config;

    @BeforeEach
    void setUp() {
        config = new AuthenticationFilter.Config();
        gatewayFilter = authenticationFilter.apply(config);
    }

    // ──────────────── Missing Authorization Header Tests ──────────────────

    @Test
    @DisplayName("Filter - missing Authorization header returns 401 Unauthorized")
    void filter_missingAuthHeader_returns401() {
        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(new org.springframework.http.HttpHeaders());
        when(exchange.getResponse()).thenReturn(response);
        when(response.setComplete()).thenReturn(Mono.empty());

        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .expectComplete()
                .verify();

        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    // ──────────────── Invalid Authorization Header Format Tests ──────────

    @Test
    @DisplayName("Filter - invalid Bearer format returns 401 Unauthorized")
    void filter_invalidBearerFormat_returns401() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "InvalidFormat token123");

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(exchange.getResponse()).thenReturn(response);
        when(response.setComplete()).thenReturn(Mono.empty());

        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .expectComplete()
                .verify();

        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Filter - Bearer without space returns 401 Unauthorized")
    void filter_bearerWithoutSpace_returns401() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearertoken123");

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(exchange.getResponse()).thenReturn(response);
        when(response.setComplete()).thenReturn(Mono.empty());

        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .expectComplete()
                .verify();

        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    // ──────────────── Invalid Token Tests ──────────────────────────────

    @Test
    @DisplayName("Filter - invalid/expired token returns 401 Unauthorized")
    void filter_invalidToken_returns401() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer invalid.expired.token");

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(jwtUtil.isTokenValid("invalid.expired.token")).thenReturn(false);
        when(exchange.getResponse()).thenReturn(response);
        when(response.setComplete()).thenReturn(Mono.empty());

        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .expectComplete()
                .verify();

        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(jwtUtil).isTokenValid("invalid.expired.token");
        verify(chain, never()).filter(any());
    }

    // ──────────────── Valid Token Tests ──────────────────────────────────

    @Test
    @DisplayName("Filter - valid token extracts username and role")
    void filter_validToken_extractsUserInfoAndPassesThrough() {
        String validToken = "valid.jwt.token";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + validToken);

        ServerHttpRequest.Builder requestBuilder = mock(ServerHttpRequest.Builder.class);
        ServerHttpRequest mutatedRequest = mock(ServerHttpRequest.class);
        ServerWebExchange.Builder exchangeBuilder = mock(ServerWebExchange.Builder.class);
        ServerWebExchange mutatedExchange = mock(ServerWebExchange.class);

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(jwtUtil.isTokenValid(validToken)).thenReturn(true);
        when(jwtUtil.extractUsername(validToken)).thenReturn("testuser");
        when(jwtUtil.extractRole(validToken)).thenReturn("USER");
        when(request.mutate()).thenReturn(requestBuilder);
        when(requestBuilder.header(anyString(), anyString())).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(mutatedRequest);
        when(exchange.mutate()).thenReturn(exchangeBuilder);
        when(exchangeBuilder.request(mutatedRequest)).thenReturn(exchangeBuilder);
        when(exchangeBuilder.build()).thenReturn(mutatedExchange);
        when(chain.filter(mutatedExchange)).thenReturn(Mono.empty());

        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .expectComplete()
                .verify();

        verify(jwtUtil).extractUsername(validToken);
        verify(jwtUtil).extractRole(validToken);
        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Filter - adds X-User-Name header to downstream request")
    void filter_validToken_addsUserNameHeader() {
        String validToken = "valid.jwt.token";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + validToken);

        ServerHttpRequest.Builder requestBuilder = mock(ServerHttpRequest.Builder.class);
        ServerHttpRequest mutatedRequest = mock(ServerHttpRequest.class);
        ServerWebExchange.Builder exchangeBuilder = mock(ServerWebExchange.Builder.class);
        ServerWebExchange mutatedExchange = mock(ServerWebExchange.class);

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(jwtUtil.isTokenValid(validToken)).thenReturn(true);
        when(jwtUtil.extractUsername(validToken)).thenReturn("johndoe");
        when(jwtUtil.extractRole(validToken)).thenReturn("ADMIN");
        when(request.mutate()).thenReturn(requestBuilder);
        when(requestBuilder.header(eq("X-User-Name"), eq("johndoe"))).thenReturn(requestBuilder);
        when(requestBuilder.header(eq("X-User-Role"), eq("ADMIN"))).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(mutatedRequest);
        when(exchange.mutate()).thenReturn(exchangeBuilder);
        when(exchangeBuilder.request(mutatedRequest)).thenReturn(exchangeBuilder);
        when(exchangeBuilder.build()).thenReturn(mutatedExchange);
        when(chain.filter(mutatedExchange)).thenReturn(Mono.empty());

        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .expectComplete()
                .verify();

        // Verify header was set
        verify(requestBuilder).header("X-User-Name", "johndoe");
        verify(requestBuilder).header("X-User-Role", "ADMIN");
    }

    @Test
    @DisplayName("Filter - preserves role information in headers")
    void filter_validToken_preservesRoleInHeader() {
        String validToken = "valid.jwt.token";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + validToken);

        ServerHttpRequest.Builder requestBuilder = mock(ServerHttpRequest.Builder.class);
        ServerHttpRequest mutatedRequest = mock(ServerHttpRequest.class);
        ServerWebExchange.Builder exchangeBuilder = mock(ServerWebExchange.Builder.class);
        ServerWebExchange mutatedExchange = mock(ServerWebExchange.class);

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(jwtUtil.isTokenValid(validToken)).thenReturn(true);
        when(jwtUtil.extractUsername(validToken)).thenReturn("admin");
        when(jwtUtil.extractRole(validToken)).thenReturn("PREMIUM");
        when(request.mutate()).thenReturn(requestBuilder);
        when(requestBuilder.header(anyString(), anyString())).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(mutatedRequest);
        when(exchange.mutate()).thenReturn(exchangeBuilder);
        when(exchangeBuilder.request(mutatedRequest)).thenReturn(exchangeBuilder);
        when(exchangeBuilder.build()).thenReturn(mutatedExchange);
        when(chain.filter(mutatedExchange)).thenReturn(Mono.empty());

        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .expectComplete()
                .verify();

        verify(requestBuilder).header("X-User-Role", "PREMIUM");
    }

    // ──────────────── Token Extraction Tests ──────────────────────────────

    @Test
    @DisplayName("Filter - correctly extracts token from Bearer header")
    void filter_validToken_extractsTokenCorrectly() {
        String tokenValue = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.token.signature";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + tokenValue);

        ServerHttpRequest.Builder requestBuilder = mock(ServerHttpRequest.Builder.class);
        ServerHttpRequest mutatedRequest = mock(ServerHttpRequest.class);
        ServerWebExchange.Builder exchangeBuilder = mock(ServerWebExchange.Builder.class);
        ServerWebExchange mutatedExchange = mock(ServerWebExchange.class);

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(jwtUtil.isTokenValid(tokenValue)).thenReturn(true);
        when(jwtUtil.extractUsername(tokenValue)).thenReturn("user");
        when(jwtUtil.extractRole(tokenValue)).thenReturn("USER");
        when(request.mutate()).thenReturn(requestBuilder);
        when(requestBuilder.header(anyString(), anyString())).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(mutatedRequest);
        when(exchange.mutate()).thenReturn(exchangeBuilder);
        when(exchangeBuilder.request(mutatedRequest)).thenReturn(exchangeBuilder);
        when(exchangeBuilder.build()).thenReturn(mutatedExchange);
        when(chain.filter(mutatedExchange)).thenReturn(Mono.empty());

        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .expectComplete()
                .verify();

        // Verify token (without 'Bearer ') was validated
        verify(jwtUtil).isTokenValid(tokenValue);
    }

    // ──────────────── Edge Cases Tests ──────────────────────────────────

    @Test
    @DisplayName("Filter - null Authorization header value returns 401")
    void filter_nullAuthHeaderValue_returns401() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, null);

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(exchange.getResponse()).thenReturn(response);
        when(response.setComplete()).thenReturn(Mono.empty());

        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .expectComplete()
                .verify();

        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Filter - configuration object is properly initialized")
    void filter_config_isProperlyInitialized() {
        AuthenticationFilter filter = new AuthenticationFilter();
        assertThat(filter).isNotNull();
    }
}

