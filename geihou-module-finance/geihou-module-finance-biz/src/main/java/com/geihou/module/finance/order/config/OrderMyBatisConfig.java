package com.geihou.module.finance.order.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus configuration for the order module.
 *
 * <p>Augments the framework-provided {@link MybatisPlusInterceptor} bean by
 * appending {@link OptimisticLockerInnerInterceptor} via a BeanPostProcessor.
 *
 * <p><b>Does NOT create a replacement MybatisPlusInterceptor bean.</b>
 * The framework's {@code GeihouMyBatisAutoConfiguration} registers
 * {@code PaginationInnerInterceptor} first; this config safely appends
 * the optimistic lock interceptor without removing pagination support.
 */
@Configuration
public class OrderMyBatisConfig implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof MybatisPlusInterceptor interceptor) {
            // Check if OptimisticLockerInnerInterceptor is already registered
            boolean hasOptimisticLocker = interceptor.getInterceptors().stream()
                    .anyMatch(i -> i instanceof OptimisticLockerInnerInterceptor);
            if (!hasOptimisticLocker) {
                interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
            }
        }
        return bean;
    }
}
