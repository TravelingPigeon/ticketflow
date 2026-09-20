package com.example.ticketflow.tenant.context;

/**
 * 当前请求所属的租户。
 *
 * <p>为什么用 {@link ThreadLocal}：MyBatis 的拦截器拿不到 Controller 的方法参数，
 * 它只能从"环境"里取值。一次请求从头到尾在同一条线程上处理，所以线程本地变量是
 * 唯一能不污染方法签名的方案。这也是它<b>唯一</b>的风险来源——
 * 线程是复用的，<b>不清理就会串号</b>：上一个请求的租户 ID 会被下一个请求读到。
 * 清理写在 {@link TenantContextFilter} 的 finally 里。</p>
 *
 * <p>没有值时返回 {@code null}：定时任务（SLA 扫描）和登录接口本来就不属于任何租户。
 * 拦截器对 null 的处理是"不插手"，理由见 {@link TicketFlowTenantLineHandler}。</p>
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}