package com.example.ticketflow.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.ticketflow.audit.domain.AuditLog;
import com.example.ticketflow.audit.domain.enums.AuditAction;
import com.example.ticketflow.audit.domain.enums.AuditResult;
import com.example.ticketflow.audit.mapper.AuditLogMapper;
import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditLogService {

    private static final Logger log =
            LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public AuditLogService(
            AuditLogMapper auditLogMapper,
            ObjectMapper objectMapper
    ) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 记一次成功的登录。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginSuccess(
            ActorType actorType,
            Long tenantId,
            Long actorId,
            String tenantCode,
            String identifier
    ) {
        recordLogin(
                actorType,
                tenantId,
                actorId,
                tenantCode,
                identifier,
                null
        );
    }

    /**
     * 记一次失败的登录。
     *
     * @param reason 失败原因（如 {@code BAD_PASSWORD}），只进审计，不返回给调用方
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailure(
            ActorType actorType,
            Long tenantId,
            Long actorId,
            String tenantCode,
            String identifier,
            String reason
    ) {
        recordLogin(
                actorType,
                tenantId,
                actorId,
                tenantCode,
                identifier,
                reason
        );
    }

    /**
     * 写一条登录审计。
     *
     * <p>三个刻意的选择：</p>
     *
     * <ol>
     *   <li><b>{@code REQUIRES_NEW}</b>：审计独立提交，不跟着调用方的事务回滚。
     *       登录失败要抛异常，异常会让外层事务回滚——审计若跟着回滚，
     *       最该被记录的失败尝试反而没留下痕迹。
     *       注意：它必须由<b>别的 Bean</b> 调用才会经过 Spring 代理生效
     *       （现在的调用方是 {@code AuthService}，满足这一点）。</li>
     *   <li><b>绝不往外抛异常</b>：审计是旁路，不能变成"谁都登不进来"的单点故障。
     *       写失败只记一条 warn，主流程该成功还成功、该失败还失败。
     *       异常在方法内部被吞掉，所以这个新事务会正常提交（哪怕里面什么都没写）。</li>
     *   <li><b>只记不可逆的标识</b>：租户编码、用户名/邮箱、失败原因。
     *       密码和密码哈希永远不进这张表。</li>
     * </ol>
     */
    private void recordLogin(
            ActorType actorType,
            Long tenantId,
            Long actorId,
            String tenantCode,
            String identifier,
            String reason
    ) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setTenantId(tenantId);
            auditLog.setActorType(actorType);
            auditLog.setActorId(actorId);
            auditLog.setAction(AuditAction.LOGIN);
            auditLog.setResult(
                    reason == null
                            ? AuditResult.SUCCESS
                            : AuditResult.FAILURE
            );
            auditLog.setDetailJson(
                    detailJson(tenantCode, identifier, reason)
            );

            auditLogMapper.insert(auditLog);
        } catch (RuntimeException exception) {
            log.warn(
                    "写登录审计失败：tenantCode={} identifier={}",
                    tenantCode,
                    identifier,
                    exception
            );
        }
    }

    private String detailJson(
            String tenantCode,
            String identifier,
            String reason
    ) {
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("tenantCode", tenantCode);
        detail.put("identifier", identifier);

        if (reason != null) {
            detail.put("reason", reason);
        }

        return objectMapper.writeValueAsString(detail);
    }

    /**
     * 查本租户的审计日志，按时间倒序。
     *
     * <p>只按 {@code tenantId} 过滤：调用方必须是从令牌拿到的租户 ID，
     * 不能来自请求参数——否则就是"传个别人的租户 ID 看别人的日志"。</p>
     */
    public Page<AuditLog> listAuditLogs(
            Long tenantId,
            long current,
            long size,
            AuditResult result
    ) {
        if (current < 1) {
            throw new BusinessException(
                    ErrorCode.INVALID_PAGE,
                    "页码必须大于等于1"
            );
        }

        if (size < 1 || size > 100) {
            throw new BusinessException(
                    ErrorCode.INVALID_PAGE_SIZE,
                    "每页数量必须在1到100之间"
            );
        }

        return auditLogMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<AuditLog>()
                        .eq(AuditLog::getTenantId, tenantId)
                        .eq(
                                result != null,
                                AuditLog::getResult,
                                result
                        )
                        .orderByDesc(AuditLog::getCreatedAt)
                        .orderByDesc(AuditLog::getId)
        );
    }
}