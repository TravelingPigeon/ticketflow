package com.example.ticketflow.ticket.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(

        @NotBlank(message = "评论内容不能为空")
        @Size(max = 5000, message = "评论内容不能超过5000个字符")
        String content
) {
}