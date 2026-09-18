package com.example.ticketflow.ticket.dto;

import com.example.ticketflow.sla.domain.enums.SlaStatus;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.domain.TicketOperation;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import com.example.ticketflow.ticket.domain.enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.List;

public record TicketDetailResponse(

        Long id,
        Long tenantId,
        String ticketNo,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        Long createdBy,
        Long customerId,
        Long assigneeId,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime firstResponseDueAt,
        LocalDateTime resolutionDueAt,
        LocalDateTime firstRespondedAt,
        LocalDateTime resolvedAt,
        SlaStatus responseSlaStatus,
        SlaStatus resolutionSlaStatus,
        List<TicketOperation> operations
) {

    public static TicketDetailResponse of(
            Ticket ticket,
            List<TicketOperation> operations
    ) {
        return new TicketDetailResponse(
                ticket.getId(),
                ticket.getTenantId(),
                ticket.getTicketNo(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getCreatedBy(),
                ticket.getCustomerId(),
                ticket.getAssigneeId(),
                ticket.getVersion(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getFirstResponseDueAt(),
                ticket.getResolutionDueAt(),
                ticket.getFirstRespondedAt(),
                ticket.getResolvedAt(),
                ticket.getResponseSlaStatus(),
                ticket.getResolutionSlaStatus(),
                List.copyOf(operations)
        );
    }
}