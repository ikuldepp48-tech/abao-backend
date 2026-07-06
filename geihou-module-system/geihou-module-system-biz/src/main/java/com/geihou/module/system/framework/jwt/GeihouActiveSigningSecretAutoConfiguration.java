package com.geihou.module.system.framework.jwt;

import com.geihou.module.system.framework.jwt.kms.GeihouKmsAutoConfiguration;
import com.geihou.module.system.framework.security.config.GeihouAuthTokenAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Conditional issuer-side active signing-secret bridge wiring.
 */
@AutoConfiguration(after = GeihouKmsAutoConfiguration.class, before = GeihouAuthTokenAutoConfiguration.class)
@ConditionalOnBean({GeihouActiveKidProvider.class, GeihouSigningSecretProvider.class})
public class GeihouActiveSigningSecretAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GeihouActiveSigningSecretProvider.class)
    public GeihouActiveSigningSecretProvider geihouActiveSigningSecretProvider(
            GeihouActiveKidProvider activeKidProvider,
            GeihouSigningSecretProvider signingSecretProvider) {
        return new GeihouResolvingActiveSigningSecretProvider(activeKidProvider, signingSecretProvider);
    }
}
