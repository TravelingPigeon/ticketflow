package com.example.ticketflow.auth.controller;

import com.example.ticketflow.auth.security.TokenBlacklist;
import com.example.ticketflow.auth.security.TokenService;
import com.example.ticketflow.auth.service.AuthService;
import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.dto.LoginRequest;
import com.example.ticketflow.user.dto.LoginResponse;
import com.example.ticketflow.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final TokenBlacklist tokenBlacklist;

    public AuthController(
            AuthService authService,
            TokenService tokenService,
            TokenBlacklist tokenBlacklist

    ) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.tokenBlacklist = tokenBlacklist;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        UserAccount user = authService.verifyCredentials(request);

        TokenService.TokenResult token = tokenService.issueToken(user);

        return ApiResponse.success(
                new LoginResponse(
                        token.accessToken(),
                        token.tokenType(),
                        token.expiresInSeconds(),
                        UserResponse.from(user)
                )
        );
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> currentUser(
            Authentication authentication
    ) {
        JwtAuthenticationToken jwtAuthentication =
                (JwtAuthenticationToken) authentication;
        Jwt jwt = jwtAuthentication.getToken();

        return ApiResponse.success(
                Map.of(
                        "username", jwt.getSubject(),
                        "tenantId", jwt.getClaim("tenantId"),
                        "actorType", jwt.getClaim("actorType"),
                        "authorities", authentication.getAuthorities()
                )
        );
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Jwt jwt = jwtAuthentication.getToken();

            Duration remaining = Duration.between(
                    Instant.now(),
                    jwt.getExpiresAt()
            );

            tokenBlacklist.blacklist(jwt.getId(), remaining);
        }

        return ApiResponse.<Void>success(null);
    }
}