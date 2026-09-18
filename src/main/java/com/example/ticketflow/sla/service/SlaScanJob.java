package com.example.ticketflow.sla.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SLA 扫描的定时触发器。
 *
 * <p>调度和业务逻辑分开：{@link SlaScanService} 可以被测试直接调用，
 * 不用等定时器，也不用为了测试去调时间。</p>
 *
 * <p>测试环境通过 {@code ticketflow.sla.scan.enabled=false} 把它整个关掉——
 * 定时任务会和测试抢数据库，制造出随机的失败。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "ticketflow.sla.scan",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SlaScanJob {

    private static final Logger log = LoggerFactory.getLogger(SlaScanJob.class);

    private final SlaScanService slaScanService;

    public SlaScanJob(SlaScanService slaScanService) {
        this.slaScanService = slaScanService;
    }

    /**
     * 用 {@code fixedDelay} 而不是 {@code fixedRate}：等上一轮跑完再等 60 秒，
     * 一轮跑慢了也不会和下一轮重叠。
     */
    @Scheduled(fixedDelayString = "${ticketflow.sla.scan.interval:60000}")
    public void scan() {
        SlaScanService.ScanSummary summary = slaScanService.scan();

        if (summary.reminded() > 0 || summary.breached() > 0) {
            log.info(
                    "SLA 扫描完成：租户 {} 个，提醒 {} 条，超时 {} 条",
                    summary.tenants(),
                    summary.reminded(),
                    summary.breached()
            );
        }
    }
}