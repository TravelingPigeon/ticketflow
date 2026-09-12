package com.example.ticketflow.customer.controller;

import com.example.ticketflow.auth.security.TokenService;
import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.customer.domain.Customer;
import com.example.ticketflow.customer.dto.CustomerLoginRequest;
import com.example.ticketflow.customer.dto.CustomerResponse;
import com.example.ticketflow.customer.dto.RegisterCustomerRequest;
import com.example.ticketflow.customer.service.CustomerService;
import com.example.ticketflow.user.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal/auth")
public class PortalAuthController {

    private final CustomerService customerService;
    private final TokenService tokenService;

    public PortalAuthController(
            CustomerService customerService,
            TokenService tokenService
    ) {
        this.customerService = customerService;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<CustomerResponse>> register(
            @Valid @RequestBody RegisterCustomerRequest request
    ) {
        Customer customer = customerService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        CustomerResponse.from(customer)
                ));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse<CustomerResponse>> login(
            @Valid @RequestBody CustomerLoginRequest request
    ) {
        Customer customer = customerService.authenticate(request);

        TokenService.TokenResult token =
                tokenService.issueCustomerToken(customer);

        return ApiResponse.success(
                new LoginResponse<>(
                        token.accessToken(),
                        token.tokenType(),
                        token.expiresInSeconds(),
                        CustomerResponse.from(customer)
                )
        );
    }
}