package com.example.ticketflow.ticket.comment.controller;

import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.auth.security.CurrentActorService;
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
    private final CurrentActorService currentActorService;

    public TicketCommentController(
            TicketCommentService ticketCommentService,
            CurrentActorService currentActorService
    ) {
        this.ticketCommentService = ticketCommentService;
        this.currentActorService = currentActorService;
    }

    @PostMapping("/{ticketId}/comments")
    public ApiResponse<TicketComment> createComment(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateCommentRequest request,
            HttpSession session,
            Authentication authentication
    ) {
        CurrentActor actor =
                currentActorService.requireActor(session, authentication);

        TicketComment comment =
                ticketCommentService.createComment(
                        actor,
                        ticketId,
                        request
                );

        return ApiResponse.success(comment);
    }

    @GetMapping("/{ticketId}/comments")
    public ApiResponse<List<TicketComment>> listComments(
            @PathVariable Long ticketId,
            HttpSession session,
            Authentication authentication
    ) {
        CurrentActor actor =
                currentActorService.requireActor(session, authentication);

        List<TicketComment> comments =
                ticketCommentService.listComments(actor, ticketId);

        return ApiResponse.success(comments);
    }
}