package com.example.ticketflow.sla.domain.enums;

/**
 * SLA 的执行状态。
 *
 * <p>{@code NORMAL → REMINDED → BREACHED} 是一次计时周期内的单向推进；
 * {@code COMPLETED} 表示"在超时之前完成了"，进入它之后不再被扫描——
 * 已经完成的事情不该再提醒。</p>
 */
public enum SlaStatus {

    NORMAL,

    REMINDED,

    BREACHED,

    COMPLETED
}