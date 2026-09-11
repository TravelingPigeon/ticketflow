package com.example.ticketflow.tenant.controller;

import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.service.TenantService;
import com.example.ticketflow.tenant.dto.CreateTenantRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }



    @PostMapping
    public ResponseEntity<ApiResponse<Tenant>> createTenant(
            @Valid @RequestBody CreateTenantRequest request
    ) {
        Tenant tenant = tenantService.createTenant(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(tenant));
    }


}
