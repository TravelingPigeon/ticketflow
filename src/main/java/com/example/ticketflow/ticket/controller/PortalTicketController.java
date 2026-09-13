package com.example.ticketflow.ticket.controller;

import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.auth.security.CurrentActorService;
import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import com.example.ticketflow.ticket.domain.enums.TicketStatus;
import com.example.ticketflow.ticket.dto.CreateTicketRequest;
import com.example.ticketflow.ticket.dto.TicketQuery;
import com.example.ticketflow.ticket.service.TicketService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal/tickets")
public class PortalTicketController {

    private final TicketService ticketService;
    private final CurrentActorService currentActorService;

    public PortalTicketController(
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
        CurrentActor actor = currentActorService.requireCustomer();

        Ticket ticket = ticketService.createTicket(actor, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(ticket));
    }

    @GetMapping
    public ApiResponse<Page<Ticket>> pageMyTickets(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority
    ) {
        CurrentActor actor = currentActorService.requireCustomer();

        TicketQuery query = new TicketQuery(status, priority, null);

        return ApiResponse.success(
                ticketService.pageTickets(actor, page, size, query)
        );
    }

    @GetMapping("/{ticketId}")
    public ApiResponse<Ticket> findMyTicket(
            @PathVariable Long ticketId
    ) {
        CurrentActor actor = currentActorService.requireCustomer();

        return ApiResponse.success(
                ticketService.findTicket(actor, ticketId)
        );
    }
}