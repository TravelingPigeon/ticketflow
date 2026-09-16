package com.example.ticketflow.user.controller;

import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.auth.security.CurrentActorService;
import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.role.dto.AssignMemberRolesRequest;
import com.example.ticketflow.role.service.MemberRoleService;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.dto.CreateUserRequest;
import com.example.ticketflow.user.dto.UserResponse;
import com.example.ticketflow.user.service.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAccountService userAccountService;
    private final CurrentActorService currentActorService;
    private final MemberRoleService memberRoleService;

    public UserController(
            UserAccountService userAccountService,
            CurrentActorService currentActorService,
            MemberRoleService memberRoleService
    ) {
        this.userAccountService = userAccountService;

        this.currentActorService = currentActorService;
        this.memberRoleService = memberRoleService;
    }

    @PreAuthorize("hasAuthority('user:create')")
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request
    ) {
        CurrentActor actor = currentActorService.requireMember();


        UserAccount user =
                userAccountService.createUser(actor.tenantId(), request);

        List<String> roles = memberRoleService.roleCodesOf(
                actor.tenantId(),
                user.getId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(UserResponse.of(user, roles)));
    }

    @PreAuthorize("hasAuthority('role:manage')")
    @PutMapping("/{userId}/roles")
    public ApiResponse<Void> replaceMemberRoles(
            @PathVariable Long userId,
            @Valid @RequestBody AssignMemberRolesRequest request
    ) {
        CurrentActor actor = currentActorService.requireMember();

        memberRoleService.replaceMemberRoles(
                actor.tenantId(),
                userId,
                request.roleCodes()
        );

        return ApiResponse.<Void>success(null);
    }
}