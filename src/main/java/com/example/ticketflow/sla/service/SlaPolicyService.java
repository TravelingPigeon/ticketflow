package com.example.ticketflow.sla.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.common.exception.ErrorCode;
import com.example.ticketflow.sla.domain.SlaPolicy;
import com.example.ticketflow.sla.dto.SlaPolicyRequest;
import com.example.ticketflow.sla.dto.SlaPolicyResponse;
import com.example.ticketflow.sla.mapper.SlaPolicyMapper;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SlaPolicyService {

    private final SlaPolicyMapper slaPolicyMapper;

    public SlaPolicyService(SlaPolicyMapper slaPolicyMapper) {
        this.slaPolicyMapper = slaPolicyMapper;
    }

    /**
     * 给新建的租户写入四条默认规则（对应 {@link TicketPriority} 的四个取值）。
     *
     * <p>默认值和 {@code V13} 迁移里给老租户补的完全一致。
     * 这里有一处和 {@code BuiltInRoles} 相同的取舍：默认值在 SQL 与 Java 各写了一份，
     * 以 Java 为准（迁移只负责给"当时已存在"的租户补数据）。</p>
     */
    @Transactional
    public void createDefaultPolicies(Long tenantId) {
        insert(tenantId, TicketPriority.HIGH, 60, 480, 30);
        insert(tenantId, TicketPriority.LOW, 480, 2880, 60);
        insert(tenantId, TicketPriority.MEDIUM, 240, 1440, 60);
        insert(tenantId, TicketPriority.URGENT, 30, 240, 15);
    }

    public List<SlaPolicyResponse> listPolicies(Long tenantId) {
        return slaPolicyMapper.selectList(
                        new LambdaQueryWrapper<SlaPolicy>()
                                .eq(SlaPolicy::getTenantId, tenantId)
                                .orderByAsc(SlaPolicy::getPriority)
                )
                .stream()
                .map(SlaPolicyResponse::from)
                .toList();
    }

    /**
     * 全量替换本租户的 SLA 规则。
     *
     * <p>为什么是全量替换而不是逐条改：规则一共三条、永远成套存在（缺一个优先级，
     * 建那个优先级的工单就算不出截止时间）。让客户端提交"完整的最终状态"，
     * 比让它逐条增删改更不容易出现"改到一半"的中间态。</p>
     */
    @Transactional
    public List<SlaPolicyResponse> replacePolicies(
            Long tenantId,
            List<SlaPolicyRequest> requests
    ) {
        requireCompleteRuleSet(requests);

        slaPolicyMapper.delete(
                new LambdaQueryWrapper<SlaPolicy>()
                        .eq(SlaPolicy::getTenantId, tenantId)
        );

        for (SlaPolicyRequest request : requests) {
            insert(
                    tenantId,
                    request.priority(),
                    request.firstResponseMinutes(),
                    request.resolutionMinutes(),
                    request.remindBeforeMinutes()
            );
        }

        return listPolicies(tenantId);
    }

    /**
     * 校验规则集合是完整且自洽的。
     *
     * <p>三条规则，缺一不可：少一个优先级，建那个优先级的工单时就算不出截止时间。</p>
     */
    private void requireCompleteRuleSet(List<SlaPolicyRequest> requests) {
        Set<TicketPriority> submitted = requests.stream()
                .map(SlaPolicyRequest::priority)
                .collect(Collectors.toSet());

        if (submitted.size() != requests.size()) {
            throw new BusinessException(
                    ErrorCode.INVALID_SLA_POLICY,
                    "同一个优先级只能配置一条规则"
            );
        }

        if (submitted.size() != TicketPriority.values().length) {
            List<String> missing = Arrays.stream(TicketPriority.values())
                    .filter(priority -> !submitted.contains(priority))
                    .map(Enum::name)
                    .toList();

            throw new BusinessException(
                    ErrorCode.INVALID_SLA_POLICY,
                    "必须为每个优先级都配置规则，缺少：" + String.join("、", missing)
            );
        }

        for (SlaPolicyRequest request : requests) {
            int remindBefore = request.remindBeforeMinutes();

            if (remindBefore >= request.firstResponseMinutes()
                    || remindBefore >= request.resolutionMinutes()) {
                throw new BusinessException(
                        ErrorCode.INVALID_SLA_POLICY,
                        "提前提醒时间必须小于首次响应时限和解决时限"
                );
            }
        }
    }

    private void insert(
            Long tenantId,
            TicketPriority priority,
            int firstResponseMinutes,
            int resolutionMinutes,
            int remindBeforeMinutes
    ) {
        SlaPolicy policy = new SlaPolicy();
        policy.setTenantId(tenantId);
        policy.setPriority(priority);
        policy.setFirstResponseMinutes(firstResponseMinutes);
        policy.setResolutionMinutes(resolutionMinutes);
        policy.setRemindBeforeMinutes(remindBeforeMinutes);
        policy.setEnabled(true);

        slaPolicyMapper.insert(policy);
    }
}
