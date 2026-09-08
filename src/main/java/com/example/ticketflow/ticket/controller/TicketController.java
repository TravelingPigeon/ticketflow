package com.example.ticketflow.ticket.controller;

import com.example.ticketflow.auth.security.CurrentTenantService;
import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.dto.CreateTicketRequest;
import com.example.ticketflow.ticket.service.TicketService;
import com.example.ticketflow.ticket.dto.UpdateTicketStatusRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final CurrentTenantService currentTenantService;

    public TicketController(
            TicketService ticketService,
            CurrentTenantService currentTenantService
    ) {
        this.ticketService = ticketService;
        this.currentTenantService = currentTenantService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Ticket>> createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            HttpSession session
    ) {
        Long tenantId =
                currentTenantService.requireTenantId(session);

        Ticket ticket =
                ticketService.createTicket(tenantId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(ticket));
    }

    @GetMapping
    public ApiResponse<Page<Ticket>> pageTickets(
            HttpSession session,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        Long tenantId =
                currentTenantService.requireTenantId(session);

        return ApiResponse.success(
                ticketService.pageTickets(tenantId, page, size)
        );
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @PatchMapping("/{ticketId}/status")
    public ApiResponse<Ticket> updateStatus(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateTicketStatusRequest request,
            HttpSession session
    ) {
        Long tenantId =
                currentTenantService.requireTenantId(session);

        return ApiResponse.success(
                ticketService.updateStatus(
                        ticketId,
                        tenantId,
                        request
                )
        );
    }

    @GetMapping("/{ticketId}")
    public ApiResponse<Ticket> findTicket(
            @PathVariable Long ticketId,
            HttpSession session
    ) {
        Long tenantId =
                currentTenantService.requireTenantId(session);

        return ApiResponse.success(
                ticketService.findTicket(ticketId, tenantId)
        );
    }

}