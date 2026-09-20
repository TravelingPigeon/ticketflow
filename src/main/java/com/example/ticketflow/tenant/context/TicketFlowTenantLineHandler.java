package com.example.ticketflow.tenant.context;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

import java.util.Locale;
import java.util.Set;

/**
 * 告诉 MyBatis-Plus：租户列叫 {@code tenant_id}，值是当前上下文里的租户 ID。
 *
 * <p>它是<b>第二道防线</b>。第一道是每个查询里显式写的 {@code tenant_id} 条件——
 * 那些条件一行都没动。留着两套是有意的：显式条件任何时候都成立（包括没有请求上下文的
 * 定时任务和测试直调），拦截器则兜住"将来有人漏写"这种情况。
 * 换句话说，<b>把拦截器删掉，隔离依然成立；把显式条件删掉，隔离就只剩一半</b>。</p>
 */
public class TicketFlowTenantLineHandler implements TenantLineHandler {

    /**
     * 没有 {@code tenant_id} 列的表，必须放过——否则拦截器会给它们拼一个不存在的列。
     *
     * <p>这三张表是查过真实库确认的（information_schema 里没有 tenant_id 的都在这里）。</p>
     */
    private static final Set<String> IGNORED_TABLES = Set.of(
            "tf_tenant",             // 租户表本身：它就是"租户"
            "tf_permission",         // 全局权限字典：所有租户共用一份
            "flyway_schema_history"  // Flyway 自己的元数据表
    );

    @Override
    public Expression getTenantId() {
        return new LongValue(TenantContext.get());
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    /**
     * 不接管的两种情况。
     *
     * <p><b>一、没有上下文。</b>定时任务（SLA 扫描）和登录接口都不属于任何租户：
     * 扫描要遍历所有租户，登录时还不知道是哪个租户。
     * 这里选择<b>不插手</b>而不是抛异常，理由是"第一道防线仍然在"（见类注释），
     * 而反过来如果抛异常，SLA 扫描会直接瘫掉——它恰恰是设计上就要跨租户工作的。</p>
     *
     * <p><b>二、表根本没有 tenant_id 列。</b>见 {@link #IGNORED_TABLES}。</p>
     */
    @Override
    public boolean ignoreTable(String tableName) {
        if (TenantContext.get() == null) {
            return true;
        }

        return IGNORED_TABLES.contains(tableName.toLowerCase(Locale.ROOT));
    }
}