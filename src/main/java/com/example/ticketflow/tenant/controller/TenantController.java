package com.example.ticketflow.tenant.controller;

import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.service.TenantService;
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
}
