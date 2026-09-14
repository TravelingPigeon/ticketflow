package com.example.ticketflow.ticket.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.ticket.domain.enums.TicketOperationType;

import java.time.LocalDateTime;

@TableName("tf_ticket_operation")
public class TicketOperation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long ticketId;

    private ActorType operatorType;

    private Long operatorId;

    private TicketOperationType operationType;

    private String fromValue;

    private String toValue;

    private LocalDateTime createdAt;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public ActorType getOperatorType() {
        return operatorType;
    }

    public void setOperatorType(ActorType operatorType) {
        this.operatorType = operatorType;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public TicketOperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(TicketOperationType operationType) {
        this.operationType = operationType;
    }

    public String getFromValue() {
        return fromValue;
    }

    public void setFromValue(String fromValue) {
        this.fromValue = fromValue;
    }

    public String getToValue() {
        return toValue;
    }

    public void setToValue(String toValue) {
        this.toValue = toValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}