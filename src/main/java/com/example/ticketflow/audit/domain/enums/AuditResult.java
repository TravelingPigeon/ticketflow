package com.example.ticketflow.audit.domain.enums;

/** 审计结果。失败的原因写在 detail_json 里，不单独建列。 */
public enum AuditResult {

    SUCCESS,

    FAILURE
}