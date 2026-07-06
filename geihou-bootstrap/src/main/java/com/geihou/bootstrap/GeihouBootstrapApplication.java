package com.geihou.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Minimal Geihou local/dev bootstrap boundary for PRD 0-01 Flyway migration smoke.
 *
 * <p>G1-04E1: @EnableScheduling activates all @Scheduled job methods in geihou modules
 * (OrderExpireJob, OrderCompletionJob, CheckoutSessionExpireJob, CartAbandonmentJob).
 *
 * <p>scanBasePackages includes the three finance job sub-packages that cover all four
 * scheduled jobs (order.job contains two jobs: OrderExpireJob and OrderCompletionJob)
 * so that the scheduled job beans are discoverable by component scanning when finance-biz
 * is on the runtime classpath. No auto-configuration exists for these jobs, so explicit
 * scan coverage is required. Only job packages are listed (not the entire finance module)
 * to keep the bootstrap boundary minimal.
 */
@SpringBootApplication(scanBasePackages = {
        "com.geihou.bootstrap",
        "com.geihou.module.finance.order.job",
        "com.geihou.module.finance.cart.job",
        "com.geihou.module.finance.checkout.job"
})
@EnableScheduling
public class GeihouBootstrapApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeihouBootstrapApplication.class, args);
    }
}
