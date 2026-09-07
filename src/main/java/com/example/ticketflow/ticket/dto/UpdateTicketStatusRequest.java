package com.example.ticketflow.ticket.dto;

import com.example.ticketflow.ticket.domain.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTicketStatusRequest(

        @NotNull(message = "工单状态不能为空")
        TicketStatus status
) {
}