package com.example.ticketflow.support;

import com.example.ticketflow.role.service.PermissionCache;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("test")
public class InMemoryPermissionCache implements PermissionCache {

    private final Map<String, Set<String>> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<Set<String>> find(Long tenantId, Long memberId) {
        return Optional.ofNullable(entries.get(key(tenantId, memberId)));
    }

    @Override
    public void put(Long tenantId, Long memberId, Set<String> permissions) {
        entries.put(key(tenantId, memberId), Set.copyOf(permissions));
    }

    @Override
    public void evict(Long tenantId, Long memberId) {
        entries.remove(key(tenantId, memberId));
    }

    /**
     * 测试专用：清空整个缓存。
     *
     * <p>测试的 {@code setUp} 会把表清空再重新插入同一批 ID，如果缓存还留着上一个用例的结果，
     * 下一个用例就会读到别人的权限——这是"缓存会让测试互相污染"的典型情况。</p>
     */
    public void clear() {
        entries.clear();
    }

    private String key(Long tenantId, Long memberId) {
        return tenantId + ":" + memberId;
    }
}
