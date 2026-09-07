package com.example.ticketflow.ticket.dto;

import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(

        @NotBlank(message = "工单编号不能为空")
        @Size(max = 32, message = "工单编号不能超过32个字符")
        String ticketNo,

        @NotBlank(message = "工单标题不能为空")
        @Size(max = 200, message = "工单标题不能超过200个字符")
        String title,

        @Size(max = 5000, message = "工单描述不能超过5000个字符")
        String description,

        TicketPriority priority
) {
}