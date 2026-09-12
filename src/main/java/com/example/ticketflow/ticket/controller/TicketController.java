package com.example.ticketflow.ticket.controller;

import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.auth.security.CurrentActorService;
import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import com.example.ticketflow.ticket.domain.enums.TicketStatus;
import com.example.ticketflow.ticket.dto.*;
import com.example.ticketflow.ticket.service.TicketService;
import jakarta.validation.Valid;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final CurrentActorService currentActorService;

    public TicketController(
            TicketService ticketService,
            CurrentActorService currentActorService
    ) {
        this.ticketService = ticketService;
        this.currentActorService = currentActorService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Ticket>> createTicket(
            @Valid @RequestBody CreateTicketRequest request
    ) {
        CurrentActor actor = currentActorService.requireActor();

        Ticket ticket =
                ticketService.createTicket(actor, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(ticket));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @PutMapping("/{ticketId}")
    public ApiResponse<Ticket> updateTicket(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateTicketRequest request
    ) {
        CurrentActor actor = currentActorService.requireActor();

        return ApiResponse.success(
                ticketService.updateTicket(actor, ticketId, request)
        );
    }

    @GetMapping
    public ApiResponse<Page<Ticket>> pageTickets(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) Long assigneeId
    ) {
        CurrentActor actor = currentActorService.requireActor();

        TicketQuery query =
                new TicketQuery(
                        status,
                        priority,
                        assigneeId
                );

        return ApiResponse.success(
                ticketService.pageTickets(
                        actor,
                        page,
                        size,
                        query
                )
        );
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @PatchMapping("/{ticketId}/status")
    public ApiResponse<Ticket> updateStatus(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateTicketStatusRequest request
    ) {
        CurrentActor actor = currentActorService.requireActor();

        return ApiResponse.success(
                ticketService.updateStatus(actor, ticketId, request)
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{ticketId}/assignee")
    public ApiResponse<Ticket> assignTicket(
            @PathVariable Long ticketId,
            @Valid @RequestBody AssignTicketRequest request
    ) {
        CurrentActor actor = currentActorService.requireActor();

        return ApiResponse.success(
                ticketService.assignTicket(actor, ticketId, request)
        );
    }

    @GetMapping("/{ticketId}")
    public ApiResponse<Ticket> findTicket(
            @PathVariable Long ticketId
    ) {
        CurrentActor actor = currentActorService.requireActor();

        return ApiResponse.success(
                ticketService.findTicket(actor, ticketId)
        );
    }

}
