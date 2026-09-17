package com.example.ticketflow.sla.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;

import java.time.LocalDateTime;

/**
 * 某个租户在某个优先级上的 SLA 规则。
 *
 * <p>一个租户有且只有三条（对应 {@link TicketPriority} 的三个取值）。
 * 工单创建时会按当时的规则算出截止时间并<b>快照</b>到工单上，之后改规则不追溯已有工单——
 * 否则规则一改，所有历史工单的截止时间集体变化，语义就乱了。</p>
 */
@TableName("tf_sla_policy")
public class SlaPolicy {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private TicketPriority priority;

    private Integer firstResponseMinutes;

    private Integer resolutionMinutes;

    private Integer remindBeforeMinutes;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public TicketPriority getPriority() { return priority; }
    public void setPriority(TicketPriority priority) { this.priority = priority; }
    public Integer getFirstResponseMinutes() { return firstResponseMinutes; }
    public void setFirstResponseMinutes(Integer firstResponseMinutes) { this.firstResponseMinutes = firstResponseMinutes; }
    public Integer getResolutionMinutes() { return resolutionMinutes; }
    public void setResolutionMinutes(Integer resolutionMinutes) { this.resolutionMinutes = resolutionMinutes; }
    public Integer getRemindBeforeMinutes() { return remindBeforeMinutes; }
    public void setRemindBeforeMinutes(Integer remindBeforeMinutes) { this.remindBeforeMinutes = remindBeforeMinutes; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}