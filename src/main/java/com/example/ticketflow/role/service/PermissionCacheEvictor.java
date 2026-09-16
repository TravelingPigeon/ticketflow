package com.example.ticketflow.role.service;

import com.example.ticketflow.role.mapper.MemberRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 权限缓存的失效入口。
 *
 * <p>单独成一个类，是为了避开循环依赖：{@link MemberRoleService} 要用 {@link RoleService}
 * （按编码找角色），如果 {@code RoleService} 反过来依赖 {@code MemberRoleService}，
 * Spring 的构造器注入会直接报循环。</p>
 *
 * <p>所有失效动作都在**事务提交之后**执行，原因见 {@link #afterCommit}。</p>
 */
@Service
public class PermissionCacheEvictor {

    private final MemberRoleMapper memberRoleMapper;
    private final PermissionCache permissionCache;

    public PermissionCacheEvictor(
            MemberRoleMapper memberRoleMapper,
            PermissionCache permissionCache
    ) {
        this.memberRoleMapper = memberRoleMapper;
        this.permissionCache = permissionCache;
    }

    public void evictMember(Long tenantId, Long memberId) {
        afterCommit(() -> permissionCache.evict(tenantId, memberId));
    }

    public void evictMembersOfRole(Long tenantId, Long roleId) {
        // 成员列表要在事务里读：这是"改之前挂了该角色的人"，也正是缓存里可能有值的那些人
        List<Long> memberIds = memberRoleMapper.selectMemberIdsByRoleId(
                tenantId,
                roleId
        );

        afterCommit(() -> memberIds.forEach(
                memberId -> permissionCache.evict(tenantId, memberId)
        ));
    }

    /**
     * 把动作推迟到事务提交之后执行。
     *
     * <p>如果在提交之前删缓存，会出现这样的时序：删了缓存但事务还没提交 →
     * 另一个请求缓存未命中 → 查库读到**旧数据** → 把旧数据写回缓存 → 本事务提交。
     * 结果是数据库是新的、缓存是旧的，而且再没人删它，只能等 TTL 过期。</p>
     *
     * <p>没有活动事务时（例如在测试里直接调用）立即执行，避免动作被静默吞掉。</p>
     */
    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                }
        );
    }
}