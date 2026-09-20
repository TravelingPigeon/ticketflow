package com.example.ticketflow.ticket.comment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.ticket.comment.domain.enums.CommentType;

import java.time.LocalDateTime;

@TableName("tf_ticket_comment")
public class TicketComment {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long ticketId;

    /** 作者是 MEMBER（企业成员）还是 CUSTOMER（外部客户） */
    private ActorType authorType;

    private Long authorId;

    /** PUBLIC_REPLY（公开回复）/ INTERNAL_NOTE（内部备注） */
    private CommentType commentType;

    private String content;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public ActorType getAuthorType() {
        return authorType;
    }

    public void setAuthorType(ActorType authorType) {
        this.authorType = authorType;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public CommentType getCommentType() {
        return commentType;
    }

    public void setCommentType(CommentType commentType) {
        this.commentType = commentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}