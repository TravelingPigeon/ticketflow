package com.example.ticketflow.role.service;

import com.example.ticketflow.role.mapper.MemberRoleMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PermissionService {

    private final MemberRoleMapper memberRoleMapper;

    public PermissionService(MemberRoleMapper memberRoleMapper) {
        this.memberRoleMapper = memberRoleMapper;
    }

    /**
     * 查询某个成员最终拥有的权限编码集合。
     *
     * <p>成员没有角色、角色被停用、或者角色没有任何权限时，返回空集合——
     * 空集合意味着"什么都做不了"，这是有意的失败即拒绝。</p>
     */
    public Set<String> permissionsOf(Long tenantId, Long memberId) {
        List<String> codes = memberRoleMapper.selectPermissionCodes(
                tenantId,
                memberId
        );

        return new LinkedHashSet<>(codes);
    }
}