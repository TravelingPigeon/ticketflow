package com.example.ticketflow.auth.security;

import java.util.Set;

/**
 * 当前调用者的身份上下文。
 *
 * <p>系统里有两类主体：企业成员（{@link ActorType#MEMBER}）与外部客户
 * （{@link ActorType#CUSTOMER}）。{@code actorId} 是"在所属表里的主键"，
 * 必须结合 {@code actorType} 才有意义——客户表的 1 号和成员表的 1 号是两个完全不同的人。</p>
 *
 * <p>{@code permissions} 是这次请求里调用者真正拥有的权限编码集合，
 * 由安全过滤器链根据令牌里的 {@code tenantId} 与 {@code actorId} 从数据库查出。
 * 客户不参与角色体系，集合为空。</p>
 */
public record CurrentActor(
        Long tenantId,
        ActorType actorType,
        Long actorId,
        String name,
        Set<String> permissions
) {

    public CurrentActor {
        permissions = Set.copyOf(permissions);
    }

    public boolean isMember() {
        return actorType == ActorType.MEMBER;
    }

    public boolean isCustomer() {
        return actorType == ActorType.CUSTOMER;
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}