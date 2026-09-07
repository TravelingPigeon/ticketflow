package com.example.ticketflow.auth.controller;

import com.example.ticketflow.auth.service.AuthService;
import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.dto.LoginRequest;
import com.example.ticketflow.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(AuthService authService) {
        this.authService = authService;
        this.securityContextRepository =
                new HttpSessionSecurityContextRepository();
    }

    @PostMapping("/login")
    public ApiResponse<UserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        UserAccount user =
                authService.verifyCredentials(request);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        user.getUsername(),
                        null,
                        java.util.List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_" + user.getRole().name()
                                )
                        )
                );

        SecurityContext securityContext =
                SecurityContextHolder.createEmptyContext();

        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        securityContextRepository.saveContext(
                securityContext,
                httpRequest,
                httpResponse
        );

        httpRequest.getSession(true).setAttribute(
                "CURRENT_TENANT_ID",
                user.getTenantId()
        );

        return ApiResponse.success(
                UserResponse.from(user)
        );
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> currentUser(
            Authentication authentication,
            HttpSession session
    ) {
        Long tenantId =
                (Long) session.getAttribute("CURRENT_TENANT_ID");

        return ApiResponse.success(
                Map.of(
                        "username", authentication.getName(),
                        "tenantId", tenantId,
                        "authorities", authentication.getAuthorities()
                )
        );
    }
}