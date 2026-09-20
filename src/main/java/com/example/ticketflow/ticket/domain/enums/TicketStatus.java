package com.example.ticketflow.ticket.domain.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 工单状态，以及状态之间的合法流转规则。
 *
 * <p>把"允许的流转"直接写在枚举里，而不是散落在 Service 的 if/switch 中：
 * 规则与状态定义放在一起，修改规则时只动一个地方，而且可以脱离 Spring 单独测试。</p>
 */
public enum TicketStatus {

    OPEN,

    PROCESSING,

    WAITING_CUSTOMER,

    RESOLVED,

    CLOSED;

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS =
            Map.of(
                    OPEN, EnumSet.of(PROCESSING),
                    PROCESSING, EnumSet.of(
                            WAITING_CUSTOMER,
                            RESOLVED
                    ),
                    WAITING_CUSTOMER, EnumSet.of(
                            PROCESSING,
                            RESOLVED
                    ),
                    RESOLVED, EnumSet.of(CLOSED, PROCESSING),
                    CLOSED, EnumSet.of(PROCESSING)
            );

    public boolean canTransitionTo(TicketStatus target) {
        return target != null
                && ALLOWED_TRANSITIONS.get(this).contains(target);
    }
}