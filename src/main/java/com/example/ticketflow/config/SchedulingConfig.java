package com.example.ticketflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启 Spring 的定时任务支持。
 *
 * <p>本项目只运行单个应用实例（见 docs/06 §12），因此 SLA 扫描不做任务租约、
 * 也不做分布式调度。将来要多实例部署时，应先关掉多余实例上的调度，再单独设计竞争方案。</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}