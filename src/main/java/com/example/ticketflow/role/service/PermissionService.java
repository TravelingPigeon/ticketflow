package com.example.ticketflow.role.service;

import com.example.ticketflow.role.mapper.MemberRoleMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class PermissionService {

    private final MemberRoleMapper memberRoleMapper;
    private final PermissionCache permissionCache;

    public PermissionService(
            MemberRoleMapper memberRoleMapper,
            PermissionCache permissionCache
    ) {
        this.memberRoleMapper = memberRoleMapper;
        this.permissionCache = permissionCache;
    }

    /**
     * 查询某个成员最终拥有的权限编码集合。
     *
     * <p>先读缓存、未命中再查库并回填（cache-aside）。空集合同样会被缓存——
     * "这个人什么都不能做"也是需要重复回答的问题。</p>
     */
    public Set<String> permissionsOf(Long tenantId, Long memberId) {
        Optional<Set<String>> cached = permissionCache.find(tenantId, memberId);

        if (cached.isPresent()) {
            return cached.get();
        }

        Set<String> permissions = new LinkedHashSet<>(
                memberRoleMapper.selectPermissionCodes(tenantId, memberId)
        );

        permissionCache.put(tenantId, memberId, permissions);

        return permissions;
    }
}