package com.geihou.module.finance.cart.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus configuration for the cart module.
 *
 * <p>Augments the framework-provided {@link MybatisPlusInterceptor} bean by
 * appending {@link OptimisticLockerInnerInterceptor} via a BeanPostProcessor.
 *
 * <p><b>Does NOT create a replacement MybatisPlusInterceptor bean.</b>
 * Safely appends the optimistic lock interceptor without removing pagination/tenant support.
 */
@Configuration
public class CartMyBatisConfig implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof MybatisPlusInterceptor interceptor) {
            boolean hasOptimisticLocker = interceptor.getInterceptors().stream()
                    .anyMatch(i -> i instanceof OptimisticLockerInnerInterceptor);
            if (!hasOptimisticLocker) {
                interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
            }
        }
        return bean;
    }
}
