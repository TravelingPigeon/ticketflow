package com.example.ticketflow.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.ticketflow.audit.domain.enums.AuditAction;
import com.example.ticketflow.audit.domain.enums.AuditResult;
import com.example.ticketflow.auth.security.ActorType;

import java.time.LocalDateTime;

/** 通用审计日志。只追加，不修改、不删除。 */
@TableName("tf_audit_log")
public class AuditLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 允许为空：租户编码本身就是错的时候，不知道是哪个租户 */
    private Long tenantId;

    private ActorType actorType;

    private Long actorId;

    private AuditAction action;

    private AuditResult result;

    /** 预留：本期恒为 null */
    private String requestId;

    private String resourceType;

    private Long resourceId;

    /** 只放不可逆的上下文（租户编码、用户名、失败原因），绝不放密码 */
    private String detailJson;

    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public ActorType getActorType() { return actorType; }
    public void setActorType(ActorType actorType) { this.actorType = actorType; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public AuditAction getAction() { return action; }
    public void setAction(AuditAction action) { this.action = action; }
    public AuditResult getResult() { return result; }
    public void setResult(AuditResult result) { this.result = result; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}