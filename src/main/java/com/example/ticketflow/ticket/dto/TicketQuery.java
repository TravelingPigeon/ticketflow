package com.example.ticketflow.ticket.dto;

import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import com.example.ticketflow.ticket.domain.enums.TicketStatus;

public record TicketQuery(
        TicketStatus status,
        TicketPriority priority,
        Long assigneeId
) {
}