package com.example.ticketflow.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.common.exception.ErrorCode;
import com.example.ticketflow.notification.domain.enums.NotificationType;
import com.example.ticketflow.notification.service.NotificationService;
import com.example.ticketflow.role.domain.Permissions;
import com.example.ticketflow.sla.domain.SlaPolicy;
import com.example.ticketflow.sla.domain.enums.SlaStatus;
import com.example.ticketflow.sla.service.SlaPolicyService;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class TicketService {

    private final TicketMapper ticketMapper;
    private final TenantMapper tenantMapper;
    private final UserAccountMapper userAccountMapper;
    private final TicketOperationMapper ticketOperationMapper;
    private final SlaPolicyService slaPolicyService;
    private final NotificationService notificationService;

    public TicketService(
            TicketMapper ticketMapper,
            TenantMapper tenantMapper,
            UserAccountMapper userAccountMapper,
            TicketOperationMapper ticketOperationMapper,
            SlaPolicyService slaPolicyService,
            NotificationService notificationService
    ) {
        this.ticketMapper = ticketMapper;
        this.tenantMapper = tenantMapper;
        this.userAccountMapper = userAccountMapper;
        this.ticketOperationMapper = ticketOperationMapper;
        this.slaPolicyService = slaPolicyService;
        this.notificationService = notificationService;
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

        // SLA 快照：按当前规则算好截止时间写进工单。
        // 用快照而不是每次实时算，是为了让"改规则"只影响之后的工单（docs/06 §9）。
        LocalDateTime createdAt = LocalDateTime.now();
        ticket.setCreatedAt(createdAt);

        SlaPolicy policy = slaPolicyService.requirePolicy(
                tenantId,
                ticket.getPriority()
        );

        ticket.setFirstResponseDueAt(
                createdAt.plusMinutes(policy.getFirstResponseMinutes())
        );
        ticket.setResolutionDueAt(
                createdAt.plusMinutes(policy.getResolutionMinutes())
        );
        ticket.setResponseSlaStatus(SlaStatus.NORMAL);
        ticket.setResolutionSlaStatus(SlaStatus.NORMAL);

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

        changeStatus(ticket, actor, request.status());

        return ticketMapper.selectById(ticketId);
    }

    /**
     * 状态推进的公共部分：校验转换表 → 条件更新（乐观锁）→ 写审计。
     *
     * <p>抽出来的原因很实际：到 F7 这一步，"改状态"已经有三个入口——
     * 成员改状态、客户回复自动恢复、客户关闭／重开。
     * 三份复制粘贴的代码就是三份乐观锁判断和三份审计格式；
     * 将来改一次错误码、加一个字段，就得记住改三个地方，漏一个就是隐性 bug。</p>
     */
    private void changeStatus(
            Ticket ticket,
            CurrentActor actor,
            TicketStatus target
    ) {
        TicketStatus previousStatus = ticket.getStatus();

        if (!previousStatus.canTransitionTo(target)) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "当前状态不允许变更为目标状态"
            );
        }

        ticket.setStatus(target);

        recordResolution(ticket);

        if (ticketMapper.updateById(ticket) == 0) {
            throw new BusinessException(
                    ErrorCode.TICKET_CONCURRENT_MODIFICATION,
                    "工单已被其他请求修改，请刷新后重试"
            );
        }

        TicketOperation operation = recordOperation(
                ticket,
                actor,
                TicketOperationType.STATUS_CHANGED,
                previousStatus.name(),
                target.name()
        );

        // 所有"进入已解决"的入口都会走到这里，通知只写一次，不用每个入口各写一遍
        if (target == TicketStatus.RESOLVED) {
            notifyCustomerAboutResolution(ticket, operation.getId());
        }
    }

    /**
     * 工单进入 RESOLVED 时通知提单客户（docs/06 §13：工单解决 → 创建客户）。
     *
     * <p>只通知"客户提的单"：成员替客户建的单没有 {@code customerId}，也就没有收件人。</p>
     *
     * <p><b>去重键里带的是操作记录的 ID</b>，不是工单 ID。原因和客户回复那次一样：
     * 一张工单可以"解决 → 重开 → 再解决"，客户每一次都应该收到"已解决"。
     * 如果键只带 {@code ticketId}，第二次通知会被唯一约束悄悄吞掉——
     * 而这条操作记录本身就是"这一次解决"的标识，用它做键既稳定又不用新造 ID。</p>
     */
    private void notifyCustomerAboutResolution(
            Ticket ticket,
            Long operationId
    ) {
        if (ticket.getCustomerId() == null) {
            return;
        }

        notificationService.notifyCustomer(
                ticket.getTenantId(),
                ticket.getCustomerId(),
                NotificationType.TICKET_RESOLVED,
                ticket,
                "你提交的工单已解决",
                "工单 " + ticket.getTicketNo()
                        + "（" + ticket.getTitle() + "）已解决，请确认；"
                        + "问题仍在的话，可以在门户申请重开。",
                "ticket:resolved:" + ticket.getId()
                        + ":operation:" + operationId
                        + ":customer:" + ticket.getCustomerId()
        );
    }

    /**
     * 客户回复"等待客户"的工单后，自动回到处理中。
     *
     * <p>对应 docs/06 §4 命令表里的 {@code reply}：<b>客户回复等待中的工单时恢复处理</b>。
     * 放在 TicketService 而不是评论服务，是因为状态机、乐观锁和审计都归工单这一侧管——
     * 两条入口（成员改状态 / 客户回复）用同一张转换表、同一套并发检查、同一张审计表。</p>
     *
     * <p>两个前提不满足时都<b>静默返回 false</b>，不报错：</p>
     * <ul>
     *   <li><b>回复的人必须是客户</b>——设计稿的额外规则是"<b>客户</b>回复等待中工单时恢复处理"。
     *       客服回复不代表客户已经补充了信息，客服要恢复处理应该显式走状态流转命令；</li>
     *   <li><b>状态必须是"等待客户"</b>——工单可能已经被客服自己改回处理中了，
     *       那客户这条评论没有任何理由失败。</li>
     * </ul>
     *
     * @return true 表示这次真的推进了状态
     */
    @Transactional
    public boolean resumeAfterCustomerReply(
            CurrentActor actor,
            Ticket ticket
    ) {
        if (!actor.isCustomer()
                || ticket.getStatus() != TicketStatus.WAITING_CUSTOMER) {
            return false;
        }

        changeStatus(ticket, actor, TicketStatus.PROCESSING);

        return true;
    }

    /**
     * 客户确认关闭自己的工单（docs/06 §4 的 {@code close} 命令）。
     *
     * <p>客户的数据范围由 {@link #findTicket} 保证：客户只能取到
     * {@code customer_id} 是自己的工单，别人的单一律是"不存在"（404）。
     * 这比"先查出来再判断是不是本人"更安全——判断分支写漏了就是越权，
     * 而查询条件写错了只会查不到。</p>
     */
    @Transactional
    public Ticket closeByCustomer(CurrentActor actor, Long ticketId) {
        Ticket ticket = findTicket(actor, ticketId);

        // 显式再判一次，为的是给出一条客户看得懂的提示；
        // changeStatus 里那次通用校验仍然保留，作为兜底
        if (ticket.getStatus() != TicketStatus.RESOLVED) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "只有已解决的工单可以由客户确认关闭"
            );
        }

        changeStatus(ticket, actor, TicketStatus.CLOSED);

        return ticketMapper.selectById(ticketId);
    }

    /**
     * 客户申请重开（docs/06 §4 的 {@code reopen} 命令）：
     * {@code RESOLVED} 或 {@code CLOSED} → {@code PROCESSING}。
     *
     * <p>这里<b>只负责状态与审计</b>。"必须填原因"那条约束由调用方
     * {@code TicketCommentService.reopenByCustomerWithReason} 完成：
     * 原因是作为一条客户公开回复留下来的，不是塞在工单字段里。</p>
     */
    @Transactional
    public Ticket reopenByCustomer(CurrentActor actor, Long ticketId) {
        Ticket ticket = findTicket(actor, ticketId);

        if (ticket.getStatus() != TicketStatus.RESOLVED
                && ticket.getStatus() != TicketStatus.CLOSED) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "只有已解决或已关闭的工单可以申请重开"
            );
        }

        changeStatus(ticket, actor, TicketStatus.PROCESSING);

        return ticketMapper.selectById(ticketId);
    }

    /**
     * 工单进入 RESOLVED 时记录解决完成。
     *
     * <p>和首次响应同样的规则：已经违约就保留违约状态、只补时间。
     * 重开后再解决不会覆盖原来的 {@code resolvedAt}，也不重开新的 SLA 周期（docs/06 §10）。</p>
     */
    private void recordResolution(Ticket ticket) {
        if (ticket.getStatus() != TicketStatus.RESOLVED) {
            return;
        }

        if (ticket.getResolvedAt() != null) {
            return;
        }

        ticket.setResolvedAt(LocalDateTime.now());

        if (ticket.getResolutionSlaStatus() != SlaStatus.BREACHED) {
            ticket.setResolutionSlaStatus(SlaStatus.COMPLETED);
        }
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

        // 给被分配的人发通知。自己把自己的单领了不用提醒——自己做的事自己知道。
        if (!assigneeId.equals(actor.actorId())) {
            notificationService.notifyMember(
                    ticket.getTenantId(),
                    assigneeId,
                    NotificationType.TICKET_ASSIGNED,
                    ticket,
                    "工单已分配给你",
                    null,
                    "ticket:assigned:" + ticket.getId()
                            + ":member:" + assigneeId
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

    private TicketOperation recordOperation(
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

        // MyBatis-Plus 插入后会把自增主键回填到实体上，调用方可以直接用
        return operation;
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
