package com.example.ticketflow.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.common.exception.ErrorCode;
import com.example.ticketflow.role.domain.Permissions;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.mapper.TenantMapper;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.domain.TicketOperation;
import com.example.ticketflow.ticket.domain.enums.TicketOperationType;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import com.example.ticketflow.ticket.domain.enums.TicketStatus;
import com.example.ticketflow.ticket.dto.*;
import com.example.ticketflow.ticket.mapper.TicketMapper;
import com.example.ticketflow.ticket.mapper.TicketOperationMapper;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.domain.enums.UserStatus;
import com.example.ticketflow.user.mapper.UserAccountMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class TicketService {

    private final TicketMapper ticketMapper;
    private final TenantMapper tenantMapper;
    private final UserAccountMapper userAccountMapper;
    private final TicketOperationMapper ticketOperationMapper;

    public TicketService(
            TicketMapper ticketMapper,
            TenantMapper tenantMapper,
            UserAccountMapper userAccountMapper,
            TicketOperationMapper ticketOperationMapper
    ) {
        this.ticketMapper = ticketMapper;
        this.tenantMapper = tenantMapper;
        this.userAccountMapper = userAccountMapper;
        this.ticketOperationMapper = ticketOperationMapper;
    }

    @Transactional
    public Ticket createTicket(
            CurrentActor actor,
            CreateTicketRequest request
    ) {
        Long tenantId = actor.tenantId();

        Tenant tenant = tenantMapper.selectById(tenantId);

        if (tenant == null) {
            throw new BusinessException(
                    ErrorCode.TENANT_NOT_FOUND,
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
                    ErrorCode.TICKET_NO_EXISTS,
                    "当前租户下工单编号已存在"
            );
        }

        Ticket ticket = new Ticket();
        ticket.setTenantId(tenantId);

        if (actor.isCustomer()) {
            // 客户提单：记录"这个问题属于哪位客户"
            ticket.setCustomerId(actor.actorId());
        } else {
            // 成员建单：记录"是谁录进来的"（内部协作单，或客服代客户提单）
            ticket.setCreatedBy(actor.actorId());
        }

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

        recordOperation(
                ticket,
                actor,
                TicketOperationType.CREATED,
                null,
                ticket.getStatus().name()
        );

        return ticketMapper.selectById(ticket.getId());
    }

    @Transactional
    public Ticket updateTicket(
            CurrentActor actor,
            Long ticketId,
            UpdateTicketRequest request
    ) {
        Ticket ticket = findTenantTicket(actor, ticketId);

        requireCanHandleTicket(ticket, actor);

        // 1. 改之前，先把旧值记下来
        String previousTitle = ticket.getTitle();
        String previousDescription = ticket.getDescription();
        TicketPriority previousPriority = ticket.getPriority();

        // 2. 应用新值
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description());
        ticket.setPriority(request.priority());

        // 3. 落库（乐观锁：版本号对不上时返回 0）
        if (ticketMapper.updateById(ticket) == 0) {
            throw new BusinessException(
                    ErrorCode.TICKET_CONCURRENT_MODIFICATION,
                    "工单已被其他请求修改，请刷新后重试"
            );
        }

        // 4. 对比一遍，看看到底改了哪些字段
        List<String> changedFields = new ArrayList<>();

        if (!Objects.equals(previousTitle, ticket.getTitle())) {
            changedFields.add("title");
        }

        if (!Objects.equals(previousDescription, ticket.getDescription())) {
            changedFields.add("description");
        }

        if (previousPriority != ticket.getPriority()) {
            changedFields.add("priority");
        }

        // 5. 一个字段都没变就不写记录
        if (!changedFields.isEmpty()) {
            recordOperation(
                    ticket,
                    actor,
                    TicketOperationType.UPDATED,
                    null,
                    String.join(",", changedFields)
            );
        }

        return ticketMapper.selectById(ticketId);
    }

    public Page<Ticket> pageTickets(
            CurrentActor actor,
            long current,
            long size
    ) {
        return pageTickets(
                actor,
                current,
                size,
                new TicketQuery(null, null, null)
        );
    }

    public Page<Ticket> pageTickets(
            CurrentActor actor,
            long current,
            long size,
            TicketQuery query
    ) {
        Long tenantId = actor.tenantId();

        Tenant tenant = tenantMapper.selectById(tenantId);

        if (tenant == null) {
            throw new BusinessException(
                    ErrorCode.TENANT_NOT_FOUND,
                    "租户不存在"
            );
        }

        if (current < 1) {
            throw new BusinessException(
                    ErrorCode.INVALID_PAGE,
                    "页码必须大于等于1"
            );
        }

        if (size < 1 || size > 100) {
            throw new BusinessException(
                    ErrorCode.INVALID_PAGE_SIZE,
                    "每页数量必须在1到100之间"
            );
        }

        if (query == null) {
            query = new TicketQuery(null, null, null);
        }

        Page<Ticket> page = new Page<>(current, size);

        LambdaQueryWrapper<Ticket> wrapper =
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getTenantId, tenantId)
                        .eq(
                                actor.isCustomer(),
                                Ticket::getCustomerId,
                                actor.actorId()
                        )
                        .eq(
                                query.status() != null,
                                Ticket::getStatus,
                                query.status()
                        )
                        .eq(
                                query.priority() != null,
                                Ticket::getPriority,
                                query.priority()
                        )
                        .eq(
                                query.assigneeId() != null,
                                Ticket::getAssigneeId,
                                query.assigneeId()
                        )
                        .orderByDesc(Ticket::getCreatedAt);

        return ticketMapper.selectPage(page, wrapper);
    }

    @Transactional
    public Ticket updateStatus(
            CurrentActor actor,
            Long ticketId,
            UpdateTicketStatusRequest request
    ) {
        Ticket ticket = findTenantTicket(actor, ticketId);

        requireCanHandleTicket(ticket, actor);

        TicketStatus previousStatus = ticket.getStatus();

        if (!previousStatus.canTransitionTo(request.status())) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "当前状态不允许变更为目标状态"
            );
        }

        ticket.setStatus(request.status());

        if (ticketMapper.updateById(ticket) == 0) {
            throw new BusinessException(
                    ErrorCode.TICKET_CONCURRENT_MODIFICATION,
                    "工单已被其他请求修改，请刷新后重试"
            );
        }

        recordOperation(
                ticket,
                actor,
                TicketOperationType.STATUS_CHANGED,
                previousStatus.name(),
                ticket.getStatus().name()
        );

        return ticketMapper.selectById(ticketId);
    }


    public Ticket findTicket(
            CurrentActor actor,
            Long ticketId
    ) {
        Ticket ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getId, ticketId)
                        .eq(Ticket::getTenantId, actor.tenantId())
                        .eq(
                                actor.isCustomer(),
                                Ticket::getCustomerId,
                                actor.actorId()
                        )
        );

        if (ticket == null) {
            throw new BusinessException(
                    ErrorCode.TICKET_NOT_FOUND,
                    "工单不存在"
            );
        }

        return ticket;
    }

    public TicketDetailResponse findTicketDetail(
            CurrentActor actor,
            Long ticketId
    ) {
        Ticket ticket = findTicket(actor, ticketId);

        return TicketDetailResponse.of(
                ticket,
                listOperationsFor(ticket)
        );
    }

    private Ticket findTenantTicket(
            CurrentActor actor,
            Long ticketId
    ) {
        Ticket ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getId, ticketId)
                        .eq(Ticket::getTenantId, actor.tenantId())
        );

        if (ticket == null) {
            throw new BusinessException(
                    ErrorCode.TICKET_NOT_FOUND,
                    "工单不存在"
            );
        }

        return ticket;
    }

    /**
     * 数据范围校验：有 {@code ticket:handle:any} 的人可以处理本租户任意工单，
     * 没有的人只能处理分配给自己那张。
     */
    private void requireCanHandleTicket(
            Ticket ticket,
            CurrentActor actor
    ) {
        if (actor.hasPermission(Permissions.TICKET_HANDLE_ANY)) {
            return;
        }

        if (!actor.actorId().equals(ticket.getAssigneeId())) {
            throw new AccessDeniedException(
                    "工单未分配给你，无法处理"
            );
        }
    }

    @Transactional
    public Ticket assignTicket(
            CurrentActor actor,
            Long ticketId,
            AssignTicketRequest request
    ) {
        Ticket ticket = findTenantTicket(actor, ticketId);

        UserAccount assignee = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getId, request.assigneeId())
                        .eq(UserAccount::getTenantId, actor.tenantId())
                        .eq(UserAccount::getStatus, UserStatus.ACTIVE)
        );

        if (assignee == null) {
            throw new BusinessException(
                    ErrorCode.ASSIGNEE_NOT_FOUND,
                    "处理人不存在或不属于当前租户"
            );
        }

        return assignTo(ticket, actor, assignee.getId(), TicketOperationType.ASSIGNED);
    }

    private Ticket assignTo(
            Ticket ticket,
            CurrentActor actor,
            Long assigneeId,
            TicketOperationType operationType
    ) {
        Long previousAssignee = ticket.getAssigneeId();
        TicketStatus previousStatus = ticket.getStatus();

        ticket.setAssigneeId(assigneeId);

        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.PROCESSING);
        }

        int updatedRows = ticketMapper.updateById(ticket);

        if (updatedRows == 0) {
            throw new BusinessException(
                    ErrorCode.TICKET_CONCURRENT_MODIFICATION,
                    "工单已被其他请求修改，请刷新后重试"
            );
        }

        recordOperation(
                ticket,
                actor,
                operationType,
                previousAssignee == null ? null : previousAssignee.toString(),
                assigneeId.toString()
        );

        if (previousStatus != ticket.getStatus()) {
            recordOperation(
                    ticket,
                    actor,
                    TicketOperationType.STATUS_CHANGED,
                    previousStatus.name(),
                    ticket.getStatus().name()
            );
        }

        return ticketMapper.selectById(ticket.getId());
    }

    @Transactional
    public Ticket claimTicket(
            CurrentActor actor,
            Long ticketId
    ) {
        Ticket ticket = findTenantTicket(actor, ticketId);

        if (ticket.getAssigneeId() != null) {
            throw new BusinessException(
                    ErrorCode.TICKET_ALREADY_ASSIGNED,
                    "工单已被领取或分配"
            );
        }

        if (ticket.getStatus() != TicketStatus.OPEN) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "只有待处理的工单可以被领取"
            );
        }

        return assignTo(ticket, actor, actor.actorId(), TicketOperationType.CLAIMED);
    }

    private void recordOperation(
            Ticket ticket,
            CurrentActor actor,
            TicketOperationType operationType,
            String fromValue,
            String toValue
    ) {
        TicketOperation operation = new TicketOperation();
        operation.setTenantId(ticket.getTenantId());
        operation.setTicketId(ticket.getId());
        operation.setOperatorType(actor.actorType());
        operation.setOperatorId(actor.actorId());
        operation.setOperationType(operationType);
        operation.setFromValue(fromValue);
        operation.setToValue(toValue);

        ticketOperationMapper.insert(operation);
    }

    public List<TicketOperation> listOperations(
            CurrentActor actor,
            Long ticketId
    ) {
        return listOperationsFor(findTicket(actor, ticketId));
    }

    private List<TicketOperation> listOperationsFor(Ticket ticket) {
        return ticketOperationMapper.selectList(
                new LambdaQueryWrapper<TicketOperation>()
                        .eq(
                                TicketOperation::getTenantId,
                                ticket.getTenantId()
                        )
                        .eq(TicketOperation::getTicketId, ticket.getId())
                        .orderByAsc(TicketOperation::getId)
        );
    }
}
