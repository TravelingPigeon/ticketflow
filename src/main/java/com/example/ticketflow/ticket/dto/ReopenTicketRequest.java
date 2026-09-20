package com.example.ticketflow.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 客户申请重开的请求体。"必须填原因"这条约束就落在这里。 */
public record ReopenTicketRequest(

        @NotBlank(message = "重开原因不能为空")
        @Size(max = 5000, message = "重开原因不能超过5000个字符")
        String reason
) {
}