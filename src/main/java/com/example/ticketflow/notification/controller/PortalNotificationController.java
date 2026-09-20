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

/**
 * 客户侧的通知入口。
 *
 * <p>和成员侧同一个 {@link NotificationService}：查询条件里带上收件人类型和收件人 ID，
 * "只能看自己的、只能改自己的"这件事由数据范围保证，不靠额外判断——所以这里不需要权限注解。</p>
 */
@RestController
@RequestMapping("/api/v1/portal/notifications")
public class PortalNotificationController {

    private final NotificationService notificationService;
    private final CurrentActorService currentActorService;

    public PortalNotificationController(
            NotificationService notificationService,
            CurrentActorService currentActorService
    ) {
        this.notificationService = notificationService;
        this.currentActorService = currentActorService;
    }

    /** 我的通知，按时间倒序；{@code unreadOnly=true} 时只返回未读的 */
    @GetMapping
    public ApiResponse<List<Notification>> listNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        CurrentActor actor = currentActorService.requireCustomer();

        return ApiResponse.success(
                notificationService.listNotifications(actor, unreadOnly)
        );
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@PathVariable Long notificationId) {
        CurrentActor actor = currentActorService.requireCustomer();

        notificationService.markRead(actor, notificationId);

        return ApiResponse.<Void>success(null);
    }
}