package com.example.ticketflow.notification.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.notification.domain.enums.NotificationType;

import java.time.LocalDateTime;

/** 站内通知。只追加，不修改（除了标记已读）。 */
@TableName("tf_notification")
public class Notification {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private ActorType recipientType;

    private Long recipientId;

    private NotificationType type;

    private Long ticketId;

    private String businessKey;

    private String title;

    private String content;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public ActorType getRecipientType() { return recipientType; }
    public void setRecipientType(ActorType recipientType) { this.recipientType = recipientType; }
    public Long getRecipientId() { return recipientId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}