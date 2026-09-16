package com.example.ticketflow.role.service;

import java.util.Optional;
import java.util.Set;

/**
 * 成员权限的缓存。
 *
 * <p>接口故意做得很窄：只会按"某个租户的某个成员"读写。
 * 底层的键怎么拼、用什么数据结构、TTL 多长，都是实现类的自由。</p>
 */
public interface PermissionCache {

    /** 命中时返回权限集合；返回 {@code Optional.empty()} 表示没有缓存 */
    Optional<Set<String>> find(Long tenantId, Long memberId);

    void put(Long tenantId, Long memberId, Set<String> permissions);

    void evict(Long tenantId, Long memberId);
}