package com.example.ticketflow.user.controller;

import com.example.ticketflow.auth.security.CurrentTenantService;
import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.dto.CreateUserRequest;
import com.example.ticketflow.user.dto.UserResponse;
import com.example.ticketflow.user.service.UserAccountService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAccountService userAccountService;
    private final CurrentTenantService currentTenantService;

    public UserController(
            UserAccountService userAccountService,
            CurrentTenantService currentTenantService
    ) {
        this.userAccountService = userAccountService;
        this.currentTenantService = currentTenantService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            HttpSession session
    ) {
        Long tenantId =
                currentTenantService.requireTenantId(session);

        UserAccount user =
                userAccountService.createUser(tenantId, request);

        UserResponse response =
                UserResponse.from(user);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}