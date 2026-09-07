package com.example.ticketflow.user.controller;

import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.dto.CreateUserRequest;
import com.example.ticketflow.user.dto.UserResponse;
import com.example.ticketflow.user.service.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAccountService userAccountService;

    public UserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request
    ) {
        UserAccount user =
                userAccountService.createUser(request);

        UserResponse response =
                UserResponse.from(user);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}