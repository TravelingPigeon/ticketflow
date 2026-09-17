package com.example.ticketflow.sla.dto;

import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 一条 SLA 规则。
 *
 * <p>这里只做"单个字段"的校验（非空、大于 0）；"提前提醒必须小于时限""优先级不能重复"
 * 这类<b>跨字段/跨元素</b>的规则放服务层——Bean Validation 表达它们要写自定义注解，
 * 对这个规模不值得。</p>
 */
public record SlaPolicyRequest(

        @NotNull(message = "优先级不能为空")
        TicketPriority priority,

        @NotNull(message = "首次响应时限不能为空")
        @Min(value = 1, message = "首次响应时限必须大于 0 分钟")
        Integer firstResponseMinutes,

        @NotNull(message = "解决时限不能为空")
        @Min(value = 1, message = "解决时限必须大于 0 分钟")
        Integer resolutionMinutes,

        @NotNull(message = "提前提醒时间不能为空")
        @Min(value = 1, message = "提前提醒时间必须大于 0 分钟")
        Integer remindBeforeMinutes
) {
}