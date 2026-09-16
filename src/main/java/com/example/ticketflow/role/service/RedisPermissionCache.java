package com.example.ticketflow.role.service;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Profile("!test")
public class RedisPermissionCache implements PermissionCache {

    private static final String KEY_PREFIX = "ticketflow:rbac:perms:";

    /**
     * 兜底有效期。
     *
     * <p>正常路径靠主动失效（改角色权限、改成员角色时删键），TTL 只负责
     * "万一漏删了，脏数据最多活多久"。所以它不需要很短。</p>
     */
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public RedisPermissionCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<Set<String>> find(Long tenantId, Long memberId) {
        String value = redisTemplate.opsForValue().get(key(tenantId, memberId));

        if (value == null) {
            return Optional.empty();
        }

        if (value.isEmpty()) {
            // 空字符串代表"查过，结论是没有任何权限"——这是有效结论，不是未命中
            return Optional.of(Set.of());
        }

        return Optional.of(
                Arrays.stream(value.split(","))
                        .collect(Collectors.toUnmodifiableSet())
        );
    }

    @Override
    public void put(Long tenantId, Long memberId, Set<String> permissions) {
        redisTemplate.opsForValue().set(
                key(tenantId, memberId),
                String.join(",", permissions),
                TTL
        );
    }

    @Override
    public void evict(Long tenantId, Long memberId) {
        redisTemplate.delete(key(tenantId, memberId));
    }

    private String key(Long tenantId, Long memberId) {
        return KEY_PREFIX + tenantId + ":" + memberId;
    }
}