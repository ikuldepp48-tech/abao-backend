package com.geihou.bootstrap.scheduling;

import com.geihou.bootstrap.GeihouBootstrapApplication;
import com.geihou.module.finance.cart.job.CartAbandonmentJob;
import com.geihou.module.finance.checkout.job.CheckoutSessionExpireJob;
import com.geihou.module.finance.order.job.OrderCompletionJob;
import com.geihou.module.finance.order.job.OrderExpireJob;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.ScheduledTask;

import com.geihou.module.finance.cart.service.CartService;
import com.geihou.module.finance.checkout.service.CheckoutService;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.service.statemachine.OrderStateMachineService;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * G1-04E1 Production scheduling activation guard.
 *
 * <p>This test lives in geihou-bootstrap (the deployment boundary module) so it can
 * directly reference {@link GeihouBootstrapApplication}. It verifies three things:
 *
 * <ol>
 *   <li><b>Production guard</b>: {@code @EnableScheduling} is present on
 *       {@link GeihouBootstrapApplication}. If someone removes it, this test fails
 *       independently of any test-config masking.</li>
 *   <li><b>Scan coverage</b>: {@code @SpringBootApplication.scanBasePackages} includes
 *       the four finance job sub-packages so the scheduled job beans are discoverable
 *       when finance-biz is on the runtime classpath. No auto-configuration exists for
 *       these jobs, so explicit scan coverage is required.</li>
 *   <li><b>Runtime registration</b>: When a Spring context starts with
 *       {@code @EnableScheduling} (from the production annotation, NOT a test-only one)
 *       and the four finance job beans are present, the
 *       {@link ScheduledAnnotationBeanPostProcessor} registers exactly 4 scheduled tasks.</li>
 * </ol>
 *
 * <p><b>Non-masking design</b>: The test config does NOT declare its own
 * {@code @EnableScheduling}. Instead, it {@code @Import}s
 * {@link GeihouBootstrapApplication} which carries the production {@code @EnableScheduling}
 * and the production {@code scanBasePackages} that discover the four job beans.
 * If the annotation is removed from production, the
 * {@code ScheduledAnnotationBeanPostProcessor} bean will not be registered and the
 * runtime registration test will fail.
 */
@SpringBootTest(classes = SchedulingActivationSmokeTest.TestConfig.class,
        properties = "spring.main.web-application-type=none")
class SchedulingActivationSmokeTest {

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledAnnotationBeanPostProcessor;

    @Autowired
    private OrderExpireJob orderExpireJob;
    @Autowired
    private OrderCompletionJob orderCompletionJob;
    @Autowired
    private CheckoutSessionExpireJob checkoutSessionExpireJob;
    @Autowired
    private CartAbandonmentJob cartAbandonmentJob;

    @Test
    void enableSchedulingAnnotationIsPresentOnBootstrapApplication() {
        assertThat(GeihouBootstrapApplication.class.isAnnotationPresent(EnableScheduling.class))
                .as("@EnableScheduling must be present on GeihouBootstrapApplication")
                .isTrue();
    }

    @Test
    void scanBasePackagesIncludesFinanceJobPackages() {
        SpringBootApplication annotation = GeihouBootstrapApplication.class
                .getAnnotation(SpringBootApplication.class);
        assertThat(annotation)
                .as("@SpringBootApplication must be present on GeihouBootstrapApplication")
                .isNotNull();
        assertThat(annotation.scanBasePackages())
                .as("scanBasePackages must include all four finance job packages for discovery")
                .contains(
                        "com.geihou.module.finance.order.job",
                        "com.geihou.module.finance.cart.job",
                        "com.geihou.module.finance.checkout.job"
                );
    }

    @Test
    void scheduledAnnotationBeanPostProcessorIsPresent() {
        assertThat(scheduledAnnotationBeanPostProcessor)
                .as("ScheduledAnnotationBeanPostProcessor must be registered (via @EnableScheduling on GeihouBootstrapApplication)")
                .isNotNull();
    }

    @Test
    void allFourScheduledTasksAreRegistered() {
        Set<ScheduledTask> tasks = scheduledAnnotationBeanPostProcessor.getScheduledTasks();
        assertThat(tasks)
                .as("Exactly 4 @Scheduled tasks should be registered (one per finance job)")
                .hasSize(4);
    }

    @Test
    void allFourJobBeansAreInstantiated() {
        assertThat(orderExpireJob).isNotNull();
        assertThat(orderCompletionJob).isNotNull();
        assertThat(checkoutSessionExpireJob).isNotNull();
        assertThat(cartAbandonmentJob).isNotNull();
    }

    @Test
    void allFourJobClassesHaveScheduledAnnotation() {
        Class<?>[] jobClasses = {
                OrderExpireJob.class,
                OrderCompletionJob.class,
                CheckoutSessionExpireJob.class,
                CartAbandonmentJob.class
        };
        for (Class<?> jobClass : jobClasses) {
            boolean hasScheduled = false;
            for (Method method : jobClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Scheduled.class)) {
                    hasScheduled = true;
                    break;
                }
            }
            assertThat(hasScheduled)
                    .as("%s must have at least one @Scheduled method", jobClass.getSimpleName())
                    .isTrue();
        }
    }

    /**
     * Test config that imports {@link GeihouBootstrapApplication} to bring in the
     * production {@code @EnableScheduling} and {@code scanBasePackages}. Does NOT
     * declare its own {@code @EnableScheduling} — scheduling activation comes solely
     * from the production annotation.
     *
     * <p>Provides mock beans for job dependencies so the context starts without
     * DataSource/MyBatis. The job beans themselves are discovered via the production
     * {@code scanBasePackages} on {@code @SpringBootApplication}.
     */
    @Configuration
    @Import(GeihouBootstrapApplication.class)
    @EnableAutoConfiguration(exclude = {
            org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
            org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class,
            org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class
    })
    static class TestConfig {

        @Bean
        OrderMapper orderMapper() {
            return mock(OrderMapper.class);
        }

        @Bean
        OrderStateMachineService orderStateMachineService() {
            return mock(OrderStateMachineService.class);
        }

        @Bean
        CartService cartService() {
            return mock(CartService.class);
        }

        @Bean
        CheckoutService checkoutService() {
            return mock(CheckoutService.class);
        }
    }
}
