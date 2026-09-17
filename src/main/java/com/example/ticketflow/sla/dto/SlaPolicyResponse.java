package com.example.ticketflow.sla.dto;

import com.example.ticketflow.sla.domain.SlaPolicy;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;

/**
 * SLA 规则的响应。
 *
 * <p>不带 {@code id}：客户端是按<b>优先级</b>引用规则的（一个租户一个优先级只有一条），
 * 暴露主键只会让调用方多存一个用不上的数字。</p>
 */
public record SlaPolicyResponse(
        TicketPriority priority,
        Integer firstResponseMinutes,
        Integer resolutionMinutes,
        Integer remindBeforeMinutes,
        Boolean enabled
) {

    public static SlaPolicyResponse from(SlaPolicy policy) {
        return new SlaPolicyResponse(
                policy.getPriority(),
                policy.getFirstResponseMinutes(),
                policy.getResolutionMinutes(),
                policy.getRemindBeforeMinutes(),
                policy.getEnabled()
        );
    }
}