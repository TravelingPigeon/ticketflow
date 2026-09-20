package com.example.ticketflow.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.example.ticketflow.tenant.context.TicketFlowTenantLineHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor =
                new MybatisPlusInterceptor();

        // 顺序有讲究：MyBatis-Plus 官方要求是
        // 多租户 / 动态表名 → 分页 → 乐观锁
        interceptor.addInnerInterceptor(
                new TenantLineInnerInterceptor(
                        new TicketFlowTenantLineHandler()
                )
        );

        interceptor.addInnerInterceptor(
                new PaginationInnerInterceptor(DbType.MYSQL) //一般分页放在最后
        );

        interceptor.addInnerInterceptor(
                new OptimisticLockerInnerInterceptor()
        );

        return interceptor;
    }
}