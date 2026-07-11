package com.geihou.module.system.framework.security.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Detects the conflict between {@code geihou.security.auth-disabled=true} and
 * {@code geihou.security.kms.enabled=true}.
 *
 * <p>{@code auth-disabled=true} enables the {@code GeihouAdminAuthUnavailableController}
 * and declares the auth subsystem unavailable. {@code kms.enabled=true} enables
 * the KMS signing-secret chain and implies the auth subsystem should run
 * normally. Both set to {@code true} is a logical contradiction; the service
 * must fail-fast rather than start in an inconsistent state.

 * <p>Plain {@code @Component} implementing {@link InitializingBean}; no
 * {@code EnvironmentPostProcessor}. The conflict is detectable at bean
 * initialization time, which is early enough.
 *
 * <p>Source: G0-04H185-SYS-503-CONTRACT.
 */
@Component
public class GeihouAuthModeConflictValidator implements InitializingBean {

    private final boolean authDisabled;
    private final boolean kmsEnabled;

    public GeihouAuthModeConflictValidator(
            @Value("${geihou.security.auth-disabled:false}") boolean authDisabled,
            @Value("${geihou.security.kms.enabled:false}") boolean kmsEnabled) {
        this.authDisabled = authDisabled;
        this.kmsEnabled = kmsEnabled;
    }

    @Override
    public void afterPropertiesSet() {
        if (authDisabled && kmsEnabled) {
            throw new IllegalStateException(
                    "Conflict: geihou.security.auth-disabled=true requires kms.enabled=false or unset; "
                            + "geihou.security.kms.enabled=true requires the real auth chain. "
                            + "Both set to true is a logical contradiction.");
        }
    }
}
