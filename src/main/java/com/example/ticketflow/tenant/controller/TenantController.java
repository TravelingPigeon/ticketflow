package com.example.ticketflow.tenant.controller;

import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.service.TenantService;
import com.example.ticketflow.tenant.dto.CreateTenantRequest;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public ApiResponse<List<Tenant>> listActiveTenants() {
        return ApiResponse.success(tenantService.findActiveTenants());
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

    @GetMapping("/page")
    public ApiResponse<Page<Tenant>> pageTenants(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.success(
                tenantService.pageActiveTenants(page, size)
        );
    }
}
