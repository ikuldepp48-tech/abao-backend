package com.geihou.module.system.framework.security.config;

import com.geihou.module.system.framework.jwt.kms.GeihouKmsProperties;
import com.geihou.module.system.framework.jwt.kms.GeihouKmsSecretClient;
import com.geihou.module.system.service.auth.EnvTwoFactorSecretKeyProvider;
import com.geihou.module.system.service.auth.GeihouTwoFactorSpringProperties;
import com.geihou.module.system.service.auth.GeihouTwoFactorSpringProperties.TwoFactorKeySource;
import com.geihou.module.system.service.auth.KmsTwoFactorSecretKeyProvider;
import com.geihou.module.system.service.auth.TwoFactorSecretKeyProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration that binds a production {@link TwoFactorSecretKeyProvider}
 * bean based on the {@code geihou.security.two-factor.key-source} property.
 *
 * <p>H157S-M: runs before {@link GeihouAuthLoginTokenServiceAutoConfiguration}
 * so that the 2FA crypto / verifier / orchestration bean graph (gated by
 * {@code @ConditionalOnBean(TwoFactorSecretKeyProvider.class)}) can load.
 *
 * <p>Two modes are supported:
 * <ul>
 *   <li><b>ENV</b> — reads a Base64-encoded AES key from a named environment
 *       variable. Fails fast at startup if the variable is missing or
 *       invalid.</li>
 *   <li><b>KMS</b> — fetches the key from a dedicated KMS secret using the
 *       existing {@link GeihouKmsSecretClient} infrastructure. Requires
 *       {@code geihou.security.kms.enabled=true}.</li>
 * </ul>
 *
 * <p>Both modes use {@code @ConditionalOnMissingBean} so that users may
 * provide a custom {@link TwoFactorSecretKeyProvider} to override the
 * auto-registered one.
 *
 * <p>This auto-configuration does not contain any real key material.
 */
@AutoConfiguration(before = GeihouAuthLoginTokenServiceAutoConfiguration.class)
@EnableConfigurationProperties(GeihouTwoFactorSpringProperties.class)
@ConditionalOnProperty(prefix = "geihou.security.two-factor", name = "key-source")
public class GeihouTwoFactorSecretKeyAutoConfiguration {

    /**
     * ENV mode: registers an {@link EnvTwoFactorSecretKeyProvider} that reads
     * a Base64 AES key from the environment variable named by
     * {@code geihou.security.two-factor.aes-key-env}.
     */
    @Configuration
    @ConditionalOnProperty(
            prefix = "geihou.security.two-factor",
            name = "key-source",
            havingValue = "ENV")
    static class EnvKeyProviderConfiguration {

        @Bean
        @ConditionalOnMissingBean(TwoFactorSecretKeyProvider.class)
        public TwoFactorSecretKeyProvider envTwoFactorSecretKeyProvider(
                GeihouTwoFactorSpringProperties properties) {
            String envVarName = properties.getAesKeyEnv();
            if (envVarName == null || envVarName.isBlank()) {
                throw new IllegalStateException(
                        "geihou.security.two-factor.aes-key-env must not be blank when key-source=ENV");
            }
            return new EnvTwoFactorSecretKeyProvider(envVarName);
        }
    }

    /**
     * KMS mode: registers a {@link KmsTwoFactorSecretKeyProvider} that fetches
     * the 2FA AES key from a dedicated KMS secret. Only activates when the
     * KMS infrastructure ({@link GeihouKmsSecretClient}) is already
     * registered — typically via {@code geihou.security.kms.enabled=true}.
     */
    @Configuration
    @ConditionalOnProperty(
            prefix = "geihou.security.two-factor",
            name = "key-source",
            havingValue = "KMS")
    static class KmsKeyProviderConfiguration {

        @Bean
        @ConditionalOnMissingBean(TwoFactorSecretKeyProvider.class)
        @ConditionalOnBean(GeihouKmsSecretClient.class)
        public TwoFactorSecretKeyProvider kmsTwoFactorSecretKeyProvider(
                GeihouTwoFactorSpringProperties properties,
                GeihouKmsProperties kmsProperties,
                GeihouKmsSecretClient secretClient) {
            String secretName = properties.getKms().getSecretName();
            if (secretName == null || secretName.isBlank()) {
                throw new IllegalStateException(
                        "geihou.security.two-factor.kms.secret-name must not be blank when key-source=KMS");
            }
            return new KmsTwoFactorSecretKeyProvider(kmsProperties, secretClient, secretName);
        }
    }
}
