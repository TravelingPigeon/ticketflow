package com.example.ticketflow.audit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.ticketflow.audit.domain.AuditLog;
import com.example.ticketflow.audit.domain.enums.AuditResult;
import com.example.ticketflow.audit.service.AuditLogService;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.auth.security.CurrentActorService;
import com.example.ticketflow.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final CurrentActorService currentActorService;

    public AuditLogController(
            AuditLogService auditLogService,
            CurrentActorService currentActorService
    ) {
        this.auditLogService = auditLogService;
        this.currentActorService = currentActorService;
    }

    /**
     * 本租户的审计日志。
     *
     * <p>租户 ID 取自令牌（{@code requireMember()}），不接受请求参数——
     * 这是"看不到别的租户"最关键的一步。</p>
     */
    @PreAuthorize("hasAuthority('audit:read')")
    @GetMapping
    public ApiResponse<Page<AuditLog>> pageAuditLogs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) AuditResult result
    ) {
        CurrentActor actor = currentActorService.requireMember();

        return ApiResponse.success(
                auditLogService.listAuditLogs(
                        actor.tenantId(),
                        page,
                        size,
                        result
                )
        );
    }
}