package com.example.ticketflow.sla.controller;

import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.auth.security.CurrentActorService;
import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.sla.dto.SlaPolicyRequest;
import com.example.ticketflow.sla.dto.SlaPolicyResponse;
import com.example.ticketflow.sla.service.SlaPolicyService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sla-policies")
public class SlaPolicyController {

    private final SlaPolicyService slaPolicyService;
    private final CurrentActorService currentActorService;

    public SlaPolicyController(
            SlaPolicyService slaPolicyService,
            CurrentActorService currentActorService
    ) {
        this.slaPolicyService = slaPolicyService;
        this.currentActorService = currentActorService;
    }

    @PreAuthorize("hasAuthority('sla:manage')")
    @GetMapping
    public ApiResponse<List<SlaPolicyResponse>> listPolicies() {
        CurrentActor actor = currentActorService.requireMember();

        return ApiResponse.success(
                slaPolicyService.listPolicies(actor.tenantId())
        );
    }

    @PreAuthorize("hasAuthority('sla:manage')")
    @PutMapping
    public ApiResponse<List<SlaPolicyResponse>> replacePolicies(
            @RequestBody List<@Valid SlaPolicyRequest> requests
    ) {
        CurrentActor actor = currentActorService.requireMember();

        return ApiResponse.success(
                slaPolicyService.replacePolicies(
                        actor.tenantId(),
                        requests
                )
        );
    }
}
