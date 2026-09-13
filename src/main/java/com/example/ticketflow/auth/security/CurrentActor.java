package com.example.ticketflow.auth.security;

import com.example.ticketflow.user.domain.enums.UserRole;

/**
 * 当前调用者的身份上下文。
 *
 * <p>系统里有两类主体：企业成员（{@link ActorType#MEMBER}）与外部客户
 * （{@link ActorType#CUSTOMER}）。{@code actorId} 是"在所属表里的主键"，
 * 必须结合 {@code actorType} 才有意义——客户表的 1 号和成员表的 1 号是两个完全不同的人。</p>
 *
 * <p>客户不参与角色体系，因此 {@code role} 对客户为 {@code null}。</p>
 */
public record CurrentActor(
        Long tenantId,
        ActorType actorType,
        Long actorId,
        String name,
        UserRole role
) {

    public boolean isMember() {
        return actorType == ActorType.MEMBER;
    }

    public boolean isCustomer() {
        return actorType == ActorType.CUSTOMER;
    }

    public boolean isAgent() {
        return isMember() && role == UserRole.AGENT;
    }
}