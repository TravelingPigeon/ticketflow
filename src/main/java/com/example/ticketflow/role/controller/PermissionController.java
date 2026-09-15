package com.example.ticketflow.role.controller;

import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.role.dto.PermissionResponse;
import com.example.ticketflow.role.mapper.PermissionMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private final PermissionMapper permissionMapper;

    public PermissionController(PermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    @PreAuthorize("hasAuthority('role:manage')")
    @GetMapping
    public ApiResponse<List<PermissionResponse>> listPermissions() {
        List<PermissionResponse> permissions = permissionMapper
                .selectList(null)
                .stream()
                .map(PermissionResponse::from)
                .sorted(Comparator.comparing(PermissionResponse::permissionGroup)
                        .thenComparing(PermissionResponse::code))
                .toList();

        return ApiResponse.success(permissions);
    }
}