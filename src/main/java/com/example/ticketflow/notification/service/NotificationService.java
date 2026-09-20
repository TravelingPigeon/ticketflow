package com.example.ticketflow.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.common.exception.ErrorCode;
import com.example.ticketflow.notification.domain.Notification;
import com.example.ticketflow.notification.domain.enums.NotificationType;
import com.example.ticketflow.notification.mapper.NotificationMapper;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.user.mapper.UserAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

@Service
public class NotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationService.class);

    private final NotificationMapper notificationMapper;
    private final UserAccountMapper userAccountMapper;

    public NotificationService(
            NotificationMapper notificationMapper,
            UserAccountMapper userAccountMapper
    ) {
        this.notificationMapper = notificationMapper;
        this.userAccountMapper = userAccountMapper;
    }

    /**
     * 给一个成员发通知。
     *
     * <p>{@code businessKey} 相同就视为"同一件事"，重复写会被唯一约束挡掉。
     * 那不是错误——它说明这件事已经通知过了，直接忽略即可。</p>
     */
    public void notifyMember(
            Long tenantId,
            Long memberId,
            NotificationType type,
            Ticket ticket,
            String title,
            String content,
            String businessKey
    ) {
        insertNotification(
                ActorType.MEMBER,
                tenantId,
                memberId,
                type,
                ticket,
                title,
                content,
                businessKey
        );
    }

    /**
     * 给一个客户发通知（工单解决、后续的客户侧提醒都走这里）。
     *
     * <p>和 {@code notifyMember} 只差收件人类型。收件人类型必须显式传，
     * 不能靠"查一下 ID 属于哪张表"——同一个租户里成员表的 1 号和客户表的 1 号
     * 是两个不同的人，猜不出来。</p>
     */
    public void notifyCustomer(
            Long tenantId,
            Long customerId,
            NotificationType type,
            Ticket ticket,
            String title,
            String content,
            String businessKey
    ) {
        insertNotification(
                ActorType.CUSTOMER,
                tenantId,
                customerId,
                type,
                ticket,
                title,
                content,
                businessKey
        );
    }

    private void insertNotification(
            ActorType recipientType,
            Long tenantId,
            Long recipientId,
            NotificationType type,
            Ticket ticket,
            String title,
            String content,
            String businessKey
    ) {
        Notification notification = new Notification();
        notification.setTenantId(tenantId);
        notification.setRecipientType(recipientType);
        notification.setRecipientId(recipientId);
        notification.setType(type);
        notification.setTicketId(ticket == null ? null : ticket.getId());
        notification.setBusinessKey(businessKey);
        notification.setTitle(title);
        notification.setContent(content);

        try {
            notificationMapper.insert(notification);
        } catch (DuplicateKeyException exception) {
            // 第二层去重：业务层已经判断过该不该发，这里再让数据库兜一次底。
            // 用 debug 而不是 warn：这是预期路径，不是异常。
            log.debug("通知已存在，跳过：{}", businessKey);
        }
    }

    /**
     * 给租户里所有启用的管理员发通知，每人一条。
     *
     * @param businessKeyOf 由成员 ID 生成 business_key——必须带上成员 ID，
     *                      否则第二个管理员会撞上第一个人已经写进去的那条
     */
    public void notifyAdmins(
            Long tenantId,
            NotificationType type,
            Ticket ticket,
            String title,
            Function<Long, String> businessKeyOf
    ) {
        notifyAdmins(tenantId, type, ticket, title, null, businessKeyOf);
    }

    /**
     * 给所有启用的管理员发通知，并带上正文。
     *
     * <p>和上面那个方法只差一个 content：SLA 通知需要告诉管理员
     * "哪张单、什么时候到期"，光看标题不够用。</p>
     */
    public void notifyAdmins(
            Long tenantId,
            NotificationType type,
            Ticket ticket,
            String title,
            String content,
            Function<Long, String> businessKeyOf
    ) {
        for (Long adminId : userAccountMapper.selectActiveAdminIds(tenantId)) {
            notifyMember(
                    tenantId,
                    adminId,
                    type,
                    ticket,
                    title,
                    content,
                    businessKeyOf.apply(adminId)
            );
        }
    }

    /**
     * 当前成员的通知，按时间倒序。
     *
     * @param unreadOnly true 时只返回未读的
     */
    public List<Notification> listNotifications(
            CurrentActor actor,
            boolean unreadOnly
    ) {
        return notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getTenantId, actor.tenantId())
                        .eq(Notification::getRecipientType, actor.actorType())
                        .eq(Notification::getRecipientId, actor.actorId())
                        .isNull(unreadOnly, Notification::getReadAt)
                        .orderByDesc(Notification::getCreatedAt)
                        .orderByDesc(Notification::getId)
        );
    }

    /**
     * 标记已读。
     *
     * <p>查询条件里带上收件人，所以"标记别人的通知"根本查不到——
     * 对外表现是 404，和"工单不可见即不存在"同一个思路。</p>
     */
    public void markRead(CurrentActor actor, Long notificationId) {
        Notification notification = notificationMapper.selectOne(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getId, notificationId)
                        .eq(Notification::getTenantId, actor.tenantId())
                        // 收件人类型必须一起比：同租户里成员 1 号和客户 1 号是两个不同的人，
                        // 少了这一条，客户就能把成员的通知标成已读（反之亦然）
                        .eq(Notification::getRecipientType, actor.actorType())
                        .eq(Notification::getRecipientId, actor.actorId())
        );

        if (notification == null) {
            throw new BusinessException(
                    ErrorCode.NOTIFICATION_NOT_FOUND,
                    "通知不存在"
            );
        }

        // 已经读过就别再写一次（也避免无谓地刷新时间）
        if (notification.getReadAt() != null) {
            return;
        }

        notification.setReadAt(LocalDateTime.now());

        notificationMapper.updateById(notification);
    }
}