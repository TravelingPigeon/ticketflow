package com.example.ticketflow.user.controller;

import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.auth.security.CurrentActorService;
import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.dto.CreateUserRequest;
import com.example.ticketflow.user.dto.UserResponse;
import com.example.ticketflow.user.service.UserAccountService;
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
    private final CurrentActorService currentActorService;

    public UserController(
            UserAccountService userAccountService,
            CurrentActorService currentActorService
    ) {
        this.userAccountService = userAccountService;

        this.currentActorService = currentActorService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request
    ) {
        CurrentActor actor = currentActorService.requireActor();


        UserAccount user =
                userAccountService.createUser(actor.tenantId(), request);

        UserResponse response =
                UserResponse.from(user);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}