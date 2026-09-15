package com.example.ticketflow.role.controller;

import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.auth.security.CurrentActorService;
import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.role.dto.CreateRoleRequest;
import com.example.ticketflow.role.dto.RoleResponse;
import com.example.ticketflow.role.dto.UpdateRolePermissionsRequest;
import com.example.ticketflow.role.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;
    private final CurrentActorService currentActorService;

    public RoleController(
            RoleService roleService,
            CurrentActorService currentActorService
    ) {
        this.roleService = roleService;
        this.currentActorService = currentActorService;
    }

    @PreAuthorize("hasAuthority('role:manage')")
    @GetMapping
    public ApiResponse<List<RoleResponse>> listRoles() {
        CurrentActor actor = currentActorService.requireMember();

        return ApiResponse.success(roleService.listRoles(actor.tenantId()));
    }

    @PreAuthorize("hasAuthority('role:manage')")
    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleRequest request
    ) {
        CurrentActor actor = currentActorService.requireMember();

        RoleResponse role = roleService.createRole(actor.tenantId(), request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(role));
    }

    @PreAuthorize("hasAuthority('role:manage')")
    @PutMapping("/{roleId}/permissions")
    public ApiResponse<RoleResponse> replaceRolePermissions(
            @PathVariable Long roleId,
            @Valid @RequestBody UpdateRolePermissionsRequest request
    ) {
        CurrentActor actor = currentActorService.requireMember();

        return ApiResponse.success(
                roleService.replaceRolePermissions(
                        actor.tenantId(),
                        roleId,
                        request.permissions()
                )
        );
    }
}