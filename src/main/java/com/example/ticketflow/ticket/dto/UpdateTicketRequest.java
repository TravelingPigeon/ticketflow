package com.example.ticketflow.ticket.dto;

import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTicketRequest(

        @NotBlank(message = "工单标题不能为空")
        @Size(max = 200, message = "工单标题不能超过200个字符")
        String title,

        @Size(max = 5000, message = "工单描述不能超过5000个字符")
        String description,

        @NotNull(message = "工单优先级不能为空")
        TicketPriority priority
) {
}