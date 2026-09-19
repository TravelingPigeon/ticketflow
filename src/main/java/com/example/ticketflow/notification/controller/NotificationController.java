package com.example.ticketflow.notification.controller;

import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.auth.security.CurrentActorService;
import com.example.ticketflow.common.api.ApiResponse;
import com.example.ticketflow.notification.domain.Notification;
import com.example.ticketflow.notification.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentActorService currentActorService;

    public NotificationController(
            NotificationService notificationService,
            CurrentActorService currentActorService
    ) {
        this.notificationService = notificationService;
        this.currentActorService = currentActorService;
    }

    /**
     * 我的通知。
     *
     * <p>不加权限注解：每个人只能看到发给自己的通知——"数据范围"本身就是隔离，
     * 和 {@code /auth/me} 一样，不存在越权可能。</p>
     */
    @GetMapping
    public ApiResponse<List<Notification>> listNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        CurrentActor actor = currentActorService.requireMember();

        return ApiResponse.success(
                notificationService.listNotifications(actor, unreadOnly)
        );
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@PathVariable Long notificationId) {
        CurrentActor actor = currentActorService.requireMember();

        notificationService.markRead(actor, notificationId);

        return ApiResponse.<Void>success(null);
    }
}