package com.example.ticketflow.audit.domain.enums;

/**
 * 审计动作。
 *
 * <p>目前只有登录。成员变更、角色变更、SLA 配置变更属于 A3 之后的步骤，
 * 加动作时只需在这里加一个枚举值——{@code action} 列是 VARCHAR，没有 CHECK 约束。</p>
 */
public enum AuditAction {

    /** 登录尝试（成功与失败都记） */
    LOGIN
}