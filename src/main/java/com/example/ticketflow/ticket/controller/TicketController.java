package com.example.ticketflow.ticket.controller;

import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.dto.CreateTicketRequest;
import com.example.ticketflow.ticket.service.TicketService;
import jakarta.validation.Valid;
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

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Ticket>> createTicket(
            @Valid @RequestBody CreateTicketRequest request
    ) {
        Ticket ticket = ticketService.createTicket(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(ticket));
    }
}