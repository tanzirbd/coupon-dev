package com.couponplatform.apigateway.filter;

import com.couponplatform.apigateway.config.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    @Autowired
    private JwtUtil jwtUtil;

    public AuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "Missing Authorization header", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Invalid Authorization header format", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            if (!jwtUtil.isTokenValid(token)) {
                return onError(exchange, "Invalid or expired JWT token", HttpStatus.UNAUTHORIZED);
            }

            // Forward user info as request headers to downstream services
            String username = jwtUtil.extractUsername(token);
            String role = jwtUtil.extractRole(token);

            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Name", username)
                    .header("X-User-Role", role)
                    .build();

            log.info("Authenticated request from user: {} with role: {}", username, role);

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        log.warn("Auth error: {}", message);
        return response.setComplete();
    }

    public static class Config {
        // Configuration properties if needed
    }
}
