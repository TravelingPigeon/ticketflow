package com.example.ticketflow.ticket.comment.controller;

import com.example.ticketflow.auth.security.CurrentTenantService;
import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.ticket.comment.domain.TicketComment;
import com.example.ticketflow.ticket.comment.dto.CreateCommentRequest;
import com.example.ticketflow.ticket.comment.service.TicketCommentService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketCommentController {

    private final TicketCommentService ticketCommentService;
    private final CurrentTenantService currentTenantService;

    public TicketCommentController(
            TicketCommentService ticketCommentService,
            CurrentTenantService currentTenantService
    ) {
        this.ticketCommentService = ticketCommentService;
        this.currentTenantService = currentTenantService;
    }

    @PostMapping("/{ticketId}/comments")
    public ApiResponse<TicketComment> createComment(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateCommentRequest request,
            HttpSession session,
            Authentication authentication
    ) {
        Long tenantId =
                currentTenantService.requireTenantId(session);

        TicketComment comment =
                ticketCommentService.createComment(
                        tenantId,
                        ticketId,
                        authentication.getName(),
                        request
                );

        return ApiResponse.success(comment);
    }

    @GetMapping("/{ticketId}/comments")
    public ApiResponse<List<TicketComment>> listComments(
            @PathVariable Long ticketId,
            HttpSession session
    ) {
        Long tenantId = currentTenantService.requireTenantId(session);

        List<TicketComment> comments =
                ticketCommentService.listComments(tenantId, ticketId);

        return ApiResponse.success(comments);
    }
}