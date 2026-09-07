package com.example.ticketflow.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.mapper.TenantMapper;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import com.example.ticketflow.ticket.domain.enums.TicketStatus;
import com.example.ticketflow.ticket.dto.CreateTicketRequest;
import com.example.ticketflow.ticket.dto.UpdateTicketStatusRequest;
import com.example.ticketflow.ticket.mapper.TicketMapper;
import org.springframework.stereotype.Service;

@Service
public class TicketService {

    private final TicketMapper ticketMapper;
    private final TenantMapper tenantMapper;

    public TicketService(
            TicketMapper ticketMapper,
            TenantMapper tenantMapper
    ) {
        this.ticketMapper = ticketMapper;
        this.tenantMapper = tenantMapper;
    }

    public Ticket createTicket(Long tenantId, CreateTicketRequest request) {
        Tenant tenant = tenantMapper.selectById(tenantId);

        if (tenant == null) {
            throw new BusinessException(
                    "TENANT_NOT_FOUND",
                    "租户不存在"
            );
        }

        Ticket existingTicket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getTenantId, tenantId)
                        .eq(Ticket::getTicketNo, request.ticketNo())
        );

        if (existingTicket != null) {
            throw new BusinessException(
                    "TICKET_NO_EXISTS",
                    "当前租户下工单编号已存在"
            );
        }

        Ticket ticket = new Ticket();
        ticket.setTenantId(tenantId);
        ticket.setTicketNo(request.ticketNo().trim());
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description());
        ticket.setStatus(TicketStatus.OPEN);

        if (request.priority() == null) {
            ticket.setPriority(TicketPriority.MEDIUM);
        } else {
            ticket.setPriority(request.priority());
        }

        ticketMapper.insert(ticket);

        return ticketMapper.selectById(ticket.getId());
    }

    public Page<Ticket> pageTickets(
            Long tenantId,
            long current,
            long size
    ) {
        Tenant tenant = tenantMapper.selectById(tenantId);

        if (tenant == null) {
            throw new BusinessException(
                    "TENANT_NOT_FOUND",
                    "租户不存在"
            );
        }

        if (current < 1) {
            throw new BusinessException(
                    "INVALID_PAGE",
                    "页码必须大于等于1"
            );
        }

        if (size < 1 || size > 100) {
            throw new BusinessException(
                    "INVALID_PAGE_SIZE",
                    "每页数量必须在1到100之间"
            );
        }

        Page<Ticket> page = new Page<>(current, size);

        LambdaQueryWrapper<Ticket> wrapper =
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getTenantId, tenantId)
                        .orderByDesc(Ticket::getCreatedAt);

        return ticketMapper.selectPage(page, wrapper);
    }

    public Ticket updateStatus(
            Long ticketId,
            UpdateTicketStatusRequest request
    ) {
        Ticket ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getId, ticketId)
                        .eq(Ticket::getTenantId, request.tenantId())
        );

        if (ticket == null) {
            throw new BusinessException(
                    "TICKET_NOT_FOUND",
                    "工单不存在"
            );
        }

        if (!isValidTransition(ticket.getStatus(), request.status())) {
            throw new BusinessException(
                    "INVALID_STATUS_TRANSITION",
                    "当前状态不允许变更为目标状态"
            );
        }

        ticket.setStatus(request.status());
        ticketMapper.updateById(ticket);

        return ticketMapper.selectById(ticketId);
    }

    private boolean isValidTransition(
            TicketStatus currentStatus,
            TicketStatus targetStatus
    ) {
        return switch (currentStatus) {
            case OPEN -> targetStatus == TicketStatus.PROCESSING;
            case PROCESSING -> targetStatus == TicketStatus.RESOLVED;
            case RESOLVED -> targetStatus == TicketStatus.CLOSED;
            case CLOSED -> false;
        };
    }

    public Ticket findTicket(Long ticketId, Long tenantId) {
        Ticket ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getId, ticketId)
                        .eq(Ticket::getTenantId, tenantId)
        );

        if (ticket == null) {
            throw new BusinessException(
                    "TICKET_NOT_FOUND",
                    "工单不存在"
            );
        }

        return ticket;
    }
}