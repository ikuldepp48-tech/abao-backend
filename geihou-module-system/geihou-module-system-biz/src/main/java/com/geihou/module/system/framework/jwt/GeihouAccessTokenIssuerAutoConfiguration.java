package com.geihou.module.system.framework.jwt;

import com.geihou.module.system.framework.security.config.GeihouAuthTokenAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Conditional issuer-side access-token bean wiring.
 */
@AutoConfiguration(after = GeihouActiveSigningSecretAutoConfiguration.class,
        before = GeihouAuthTokenAutoConfiguration.class)
@ConditionalOnBean(GeihouActiveSigningSecretProvider.class)
public class GeihouAccessTokenIssuerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GeihouAccessTokenIssuer.class)
    public GeihouAccessTokenIssuer geihouAccessTokenIssuer(
            GeihouActiveSigningSecretProvider activeSigningSecretProvider) {
        return new GeihouAccessTokenIssuer(activeSigningSecretProvider);
    }
}
