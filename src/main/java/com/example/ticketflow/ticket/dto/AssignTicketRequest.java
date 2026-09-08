package com.example.ticketflow.ticket.dto;

import jakarta.validation.constraints.NotNull;

public record AssignTicketRequest(

        @NotNull(message = "处理人不能为空")
        Long assigneeId
) {
}