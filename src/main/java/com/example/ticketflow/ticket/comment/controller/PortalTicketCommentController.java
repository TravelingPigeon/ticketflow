package com.example.ticketflow.ticket.comment.controller;

import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.auth.security.CurrentActorService;
import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.ticket.comment.domain.TicketComment;
import com.example.ticketflow.ticket.comment.dto.CreateCommentRequest;
import com.example.ticketflow.ticket.comment.service.TicketCommentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户侧的评论入口。
 *
 * <p>和成员侧的 {@link TicketCommentController} 是两个入口、同一个
 * {@link TicketCommentService}：作者身份、评论类型校验、可见性过滤、首次响应判定
 * 全在服务层，所以两个入口不会各写一份规则。</p>
 *
 * <p>这里<b>没有权限注解</b>，和门户的其他接口一致：门户走
 * {@code requireCustomer()}，客户不参与角色权限体系；数据范围由服务层的
 * "客户只能看到自己的工单"保证（看不见就返回 404，不返回 403）。</p>
 */
@RestController
@RequestMapping("/api/v1/portal/tickets")
public class PortalTicketCommentController {

    private final TicketCommentService ticketCommentService;
    private final CurrentActorService currentActorService;

    public PortalTicketCommentController(
            TicketCommentService ticketCommentService,
            CurrentActorService currentActorService
    ) {
        this.ticketCommentService = ticketCommentService;
        this.currentActorService = currentActorService;
    }

    @PostMapping("/{ticketId}/comments")
    public ApiResponse<TicketComment> createComment(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        CurrentActor actor = currentActorService.requireCustomer();

        return ApiResponse.success(
                ticketCommentService.createComment(actor, ticketId, request)
        );
    }

    @GetMapping("/{ticketId}/comments")
    public ApiResponse<List<TicketComment>> listComments(
            @PathVariable Long ticketId
    ) {
        CurrentActor actor = currentActorService.requireCustomer();

        return ApiResponse.success(
                ticketCommentService.listComments(actor, ticketId)
        );
    }
}