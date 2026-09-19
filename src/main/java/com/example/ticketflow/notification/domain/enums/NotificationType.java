package com.example.ticketflow.notification.domain.enums;

/** 通知类型。 */
public enum NotificationType {

    /** 工单被分配给你 */
    TICKET_ASSIGNED,

    /** 你负责的工单即将超出 SLA */
    SLA_DUE_SOON,

    /** 你负责的工单已经超出 SLA */
    SLA_BREACHED
}