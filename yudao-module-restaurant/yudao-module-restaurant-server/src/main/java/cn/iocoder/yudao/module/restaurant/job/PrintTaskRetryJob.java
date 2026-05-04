package cn.iocoder.yudao.module.restaurant.job;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.restaurant.service.printer.PrintTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 打印任务重试定时任务 —— 每2分钟扫描失败任务并重试
 */
@Component
@Slf4j
public class PrintTaskRetryJob {

    @Resource
    private PrintTaskService printTaskService;

    @Scheduled(cron = "0 */2 * * * ?")
    public void retryFailedPrintTasks() {
        TenantUtils.executeIgnore(() -> {
            try {
                printTaskService.retryFailedTasks();
            } catch (Exception e) {
                log.error("[PrintTaskRetryJob][重试异常]", e);
            }
        });
    }

}
