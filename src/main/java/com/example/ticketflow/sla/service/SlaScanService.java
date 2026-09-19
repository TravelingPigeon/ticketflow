package com.example.ticketflow.sla.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.ticketflow.notification.domain.enums.NotificationType;
import com.example.ticketflow.notification.service.NotificationService;
import com.example.ticketflow.sla.domain.SlaPolicy;
import com.example.ticketflow.sla.domain.enums.SlaStatus;
import com.example.ticketflow.sla.mapper.SlaPolicyMapper;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.mapper.TenantMapper;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import com.example.ticketflow.ticket.mapper.TicketMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SLA 定时扫描：把"即将到期"和"已经超时"推进到对应的状态。
 *
 * <p>四个分类各自一条查询，每类每租户最多处理 {@value #BATCH_SIZE} 条；
 * 这一轮没处理完的留给下一轮，所以单轮的工作量是有上限的。</p>
 *
 * <p>状态推进一律用<b>条件更新</b>（{@code WHERE status = 期望的旧值}）并检查影响行数：
 * 两个扫描进程、或者"扫描"和"用户回复"撞在一起时，只有一个能改成功。
 * 这也让 L2e 的通知天然不会重复——只有改成功的那次才发。</p>
 *
 * <p>整个扫描<b>不开事务</b>：每张工单的推进都是独立的一次短更新。
 * 开一个大事务会把锁持有到扫描结束，反而更容易和请求撞车。</p>
 */
@Service
public class SlaScanService {

    /** 每类查询每个租户一次最多处理多少条 */
    private static final int BATCH_SIZE = 100;

    /** 通知正文里的时间：精确到分钟就够，秒对客服没有意义 */
    private static final DateTimeFormatter NOTIFICATION_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TenantMapper tenantMapper;
    private final SlaPolicyMapper slaPolicyMapper;
    private final TicketMapper ticketMapper;
    private final NotificationService notificationService;

    public SlaScanService(
            TenantMapper tenantMapper,
            SlaPolicyMapper slaPolicyMapper,
            TicketMapper ticketMapper,
            NotificationService notificationService
    ) {
        this.tenantMapper = tenantMapper;
        this.slaPolicyMapper = slaPolicyMapper;
        this.ticketMapper = ticketMapper;
        this.notificationService = notificationService;
    }

    public ScanSummary scan() {
        LocalDateTime now = LocalDateTime.now();

        List<Tenant> tenants = tenantMapper.selectList(
                new LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getStatus, "ACTIVE")
        );

        int reminded = 0;
        int breached = 0;

        for (Tenant tenant : tenants) {
            Map<TicketPriority, SlaPolicy> policies = policiesOf(tenant.getId());

            reminded += remindDueResponses(tenant.getId(), policies, now);
            breached += breachOverdueResponses(tenant.getId(), now);
            reminded += remindDueResolutions(tenant.getId(), policies, now);
            breached += breachOverdueResolutions(tenant.getId(), now);
        }

        return new ScanSummary(tenants.size(), reminded, breached);
    }

    /** 一次扫描的结果，用于打日志和测试断言 */
    public record ScanSummary(int tenants, int reminded, int breached) {
    }

    private Map<TicketPriority, SlaPolicy> policiesOf(Long tenantId) {
        return slaPolicyMapper.selectList(
                        new LambdaQueryWrapper<SlaPolicy>()
                                .eq(SlaPolicy::getTenantId, tenantId)
                )
                .stream()
                .collect(Collectors.toMap(
                        SlaPolicy::getPriority,
                        Function.identity()
                ));
    }

    /**
     * 响应即将到期 → REMINDED。
     *
     * <p>先用"该租户最大的提醒阈值"粗筛，再按每张工单自己的规则精筛——
     * 这样不用在 SQL 里拼数据库特有的日期函数。</p>
     */
    private int remindDueResponses(
            Long tenantId,
            Map<TicketPriority, SlaPolicy> policies,
            LocalDateTime now
    ) {
        int maxRemindBefore = policies.values()
                .stream()
                .mapToInt(SlaPolicy::getRemindBeforeMinutes)
                .max()
                .orElse(0);

        if (maxRemindBefore == 0) {
            return 0;
        }

        List<Ticket> candidates = ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getTenantId, tenantId)
                        .eq(Ticket::getResponseSlaStatus, SlaStatus.NORMAL)
                        .isNull(Ticket::getFirstRespondedAt)
                        .gt(Ticket::getFirstResponseDueAt, now)
                        .le(
                                Ticket::getFirstResponseDueAt,
                                now.plusMinutes(maxRemindBefore)
                        )
                        .orderByAsc(Ticket::getFirstResponseDueAt)
                        .last("LIMIT " + BATCH_SIZE)
        );

        int reminded = 0;

        for (Ticket ticket : candidates) {
            SlaPolicy policy = policies.get(ticket.getPriority());

            if (!withinRemindWindow(
                    ticket.getFirstResponseDueAt(),
                    policy,
                    now
            )) {
                continue;
            }

            if (advanceResponseStatus(
                    ticket.getId(),
                    SlaStatus.NORMAL,
                    SlaStatus.REMINDED
            )) {
                reminded++;
                notifyResponseDueSoon(tenantId, ticket);
            }
        }

        return reminded;
    }

    /** 响应已超时 → BREACHED */
    private int breachOverdueResponses(Long tenantId, LocalDateTime now) {
        List<Ticket> candidates = ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getTenantId, tenantId)
                        .in(
                                Ticket::getResponseSlaStatus,
                                SlaStatus.NORMAL,
                                SlaStatus.REMINDED
                        )
                        .isNull(Ticket::getFirstRespondedAt)
                        .le(Ticket::getFirstResponseDueAt, now)
                        .orderByAsc(Ticket::getFirstResponseDueAt)
                        .last("LIMIT " + BATCH_SIZE)
        );

        int breached = 0;

        for (Ticket ticket : candidates) {
            if (advanceResponseStatus(
                    ticket.getId(),
                    ticket.getResponseSlaStatus(),
                    SlaStatus.BREACHED
            )) {
                breached++;
                notifyResponseBreached(tenantId, ticket);
            }
        }

        return breached;
    }

    /** 解决即将到期 → REMINDED */
    private int remindDueResolutions(
            Long tenantId,
            Map<TicketPriority, SlaPolicy> policies,
            LocalDateTime now
    ) {
        int maxRemindBefore = policies.values()
                .stream()
                .mapToInt(SlaPolicy::getRemindBeforeMinutes)
                .max()
                .orElse(0);

        if (maxRemindBefore == 0) {
            return 0;
        }

        List<Ticket> candidates = ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getTenantId, tenantId)
                        .eq(Ticket::getResolutionSlaStatus, SlaStatus.NORMAL)
                        .gt(Ticket::getResolutionDueAt, now)
                        .le(
                                Ticket::getResolutionDueAt,
                                now.plusMinutes(maxRemindBefore)
                        )
                        .orderByAsc(Ticket::getResolutionDueAt)
                        .last("LIMIT " + BATCH_SIZE)
        );

        int reminded = 0;

        for (Ticket ticket : candidates) {
            if (!withinRemindWindow(
                    ticket.getResolutionDueAt(),
                    policies.get(ticket.getPriority()),
                    now
            )) {
                continue;
            }

            if (advanceResolutionStatus(
                    ticket.getId(),
                    SlaStatus.NORMAL,
                    SlaStatus.REMINDED
            )) {
                reminded++;
                notifyResolutionDueSoon(tenantId, ticket);
            }
        }

        return reminded;
    }

    /** 解决已超时 → BREACHED */
    private int breachOverdueResolutions(Long tenantId, LocalDateTime now) {
        List<Ticket> candidates = ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getTenantId, tenantId)
                        .in(
                                Ticket::getResolutionSlaStatus,
                                SlaStatus.NORMAL,
                                SlaStatus.REMINDED
                        )
                        .le(Ticket::getResolutionDueAt, now)
                        .orderByAsc(Ticket::getResolutionDueAt)
                        .last("LIMIT " + BATCH_SIZE)
        );

        int breached = 0;

        for (Ticket ticket : candidates) {
            if (advanceResolutionStatus(
                    ticket.getId(),
                    ticket.getResolutionSlaStatus(),
                    SlaStatus.BREACHED
            )) {
                breached++;
                notifyResolutionBreached(tenantId, ticket);
            }
        }

        return breached;
    }

    /**
     * 截止时间是否落在"提前提醒窗口"内。
     *
     * <p>规则缺失时返回 false：不提醒，也不推进状态。
     * 正常情况下建单时就会因为缺规则而失败，这里只是防御历史数据。</p>
     */
    private boolean withinRemindWindow(
            LocalDateTime dueAt,
            SlaPolicy policy,
            LocalDateTime now
    ) {
        if (policy == null) {
            return false;
        }

        return !dueAt.isAfter(now.plusMinutes(policy.getRemindBeforeMinutes()));
    }

    /**
     * 条件推进响应 SLA 状态。
     *
     * <p>返回 true 表示这次调用真的改成功了（影响行数 1）——
     * 并发下只有一个人会拿到 true。</p>
     */
    private boolean advanceResponseStatus(
            Long ticketId,
            SlaStatus from,
            SlaStatus to
    ) {
        int updated = ticketMapper.update(
                null,
                new LambdaUpdateWrapper<Ticket>()
                        .eq(Ticket::getId, ticketId)
                        .eq(Ticket::getResponseSlaStatus, from)
                        .set(Ticket::getResponseSlaStatus, to)
        );

        return updated == 1;
    }

    private boolean advanceResolutionStatus(
            Long ticketId,
            SlaStatus from,
            SlaStatus to
    ) {
        int updated = ticketMapper.update(
                null,
                new LambdaUpdateWrapper<Ticket>()
                        .eq(Ticket::getId, ticketId)
                        .eq(Ticket::getResolutionSlaStatus, from)
                        .set(Ticket::getResolutionSlaStatus, to)
        );

        return updated == 1;
    }

    // ------------------------------------------------------------------
    // L2e-2：状态推进成功之后才发通知
    // ------------------------------------------------------------------

    /** 响应 SLA 即将超时 */
    private void notifyResponseDueSoon(Long tenantId, Ticket ticket) {
        notifyDueSoon(
                tenantId,
                ticket,
                "response",
                "工单即将超出响应时限",
                "工单 " + ticket.getTicketNo()
                        + "（" + ticket.getTitle() + "）将在 "
                        + formatTime(ticket.getFirstResponseDueAt())
                        + " 超出首次响应时限，请尽快处理。"
        );
    }

    /** 解决 SLA 即将超时 */
    private void notifyResolutionDueSoon(Long tenantId, Ticket ticket) {
        notifyDueSoon(
                tenantId,
                ticket,
                "resolution",
                "工单即将超出解决时限",
                "工单 " + ticket.getTicketNo()
                        + "（" + ticket.getTitle() + "）将在 "
                        + formatTime(ticket.getResolutionDueAt())
                        + " 超出解决时限，请尽快处理。"
        );
    }

    /**
     * "即将超时"的共同逻辑：有负责人就只发给他；没有负责人发给管理员。
     *
     * <p>提醒是<b>催办</b>，催的是干活的人；管理员只在没人干活时兜底。
     * 否则一张工单会同时打扰一整队人，提醒就变成了噪音。</p>
     */
    private void notifyDueSoon(
            Long tenantId,
            Ticket ticket,
            String side,
            String title,
            String content
    ) {
        Long assigneeId = ticket.getAssigneeId();

        if (assigneeId != null) {
            notificationService.notifyMember(
                    tenantId,
                    assigneeId,
                    NotificationType.SLA_DUE_SOON,
                    ticket,
                    title,
                    content,
                    slaBusinessKey(side, "reminder", ticket.getId(), assigneeId)
            );
            return;
        }

        notificationService.notifyAdmins(
                tenantId,
                NotificationType.SLA_DUE_SOON,
                ticket,
                title,
                content,
                adminId -> slaBusinessKey(
                        side,
                        "reminder",
                        ticket.getId(),
                        adminId
                )
        );
    }

    /** 响应 SLA 已超时 */
    private void notifyResponseBreached(Long tenantId, Ticket ticket) {
        notifyBreached(
                tenantId,
                ticket,
                "response",
                "工单已超出响应时限",
                "工单 " + ticket.getTicketNo()
                        + "（" + ticket.getTitle() + "）已于 "
                        + formatTime(ticket.getFirstResponseDueAt())
                        + " 超出首次响应时限。"
        );
    }

    /** 解决 SLA 已超时 */
    private void notifyResolutionBreached(Long tenantId, Ticket ticket) {
        notifyBreached(
                tenantId,
                ticket,
                "resolution",
                "工单已超出解决时限",
                "工单 " + ticket.getTicketNo()
                        + "（" + ticket.getTitle() + "）已于 "
                        + formatTime(ticket.getResolutionDueAt())
                        + " 超出解决时限。"
        );
    }

    /**
     * "已超时"的共同逻辑：负责人要知道，管理员也要知道——超时是要被追责的事。
     *
     * <p>如果负责人本人就是管理员，两次调用生成的是<b>同一个</b> business_key，
     * 数据库唯一约束会把第二条挡掉：他不会收到两条一样的通知。
     * 这也是 business_key 里必须带收件人 ID 的原因。</p>
     */
    private void notifyBreached(
            Long tenantId,
            Ticket ticket,
            String side,
            String title,
            String content
    ) {
        Long assigneeId = ticket.getAssigneeId();

        if (assigneeId != null) {
            notificationService.notifyMember(
                    tenantId,
                    assigneeId,
                    NotificationType.SLA_BREACHED,
                    ticket,
                    title,
                    content,
                    slaBusinessKey(side, "breached", ticket.getId(), assigneeId)
            );
        }

        notificationService.notifyAdmins(
                tenantId,
                NotificationType.SLA_BREACHED,
                ticket,
                title,
                content,
                adminId -> slaBusinessKey(
                        side,
                        "breached",
                        ticket.getId(),
                        adminId
                )
        );
    }

    /**
     * 去重键：{@code sla:response:breached:17:member:3}。
     *
     * <p>格式来自设计文档：{@code 事件:标识:member:成员ID}。
     * 带收件人 ID 是为了让"同一件事发给多个人"各占一行，
     * 而"同一件事发给同一个人"永远只有一行。</p>
     */
    private String slaBusinessKey(
            String side,
            String level,
            Long ticketId,
            Long memberId
    ) {
        return "sla:" + side + ":" + level
                + ":" + ticketId
                + ":member:" + memberId;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "未知时间" : NOTIFICATION_TIME.format(time);
    }
}
