package com.geihou.module.system.framework.jwt.kms;

import com.aliyun.dkms.gcs.openapi.models.Config;
import com.aliyun.dkms.gcs.sdk.Client;
import com.geihou.module.system.framework.jwt.GeihouActiveKidProvider;
import com.geihou.module.system.framework.jwt.GeihouSigningSecretProvider;
import com.geihou.module.system.framework.security.config.GeihouAuthTokenAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Disabled-by-default KMS signing-secret provider wiring.
 */
@AutoConfiguration(before = GeihouAuthTokenAutoConfiguration.class)
@EnableConfigurationProperties(GeihouKmsSpringProperties.class)
@ConditionalOnProperty(prefix = "geihou.security.kms", name = "enabled", havingValue = "true")
public class GeihouKmsAutoConfiguration {

    private static final String USER_AGENT = "geihou-auth-kms/h111";

    @Bean
    @ConditionalOnMissingBean
    public GeihouKmsProperties geihouKmsProperties(GeihouKmsSpringProperties springProperties) {
        return springProperties.toDomainProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public GeihouKmsClientKeyPasswordResolver geihouKmsClientKeyPasswordResolver() {
        return new GeihouKmsClientKeyPasswordResolver(System::getenv);
    }

    @Bean
    @ConditionalOnMissingBean
    public GeihouKmsSecretNameMapper geihouKmsSecretNameMapper(GeihouKmsProperties properties) {
        return new GeihouKmsSecretNameMapper(properties.secretNamePrefix());
    }

    @Bean
    @ConditionalOnMissingBean
    public GeihouKmsSigningSecretDecoder geihouKmsSigningSecretDecoder() {
        return new GeihouKmsSigningSecretDecoder();
    }

    @Bean
    @ConditionalOnMissingBean
    public AliyunDkmsSdkClientFactory aliyunDkmsSdkClientFactory() {
        return new AliyunDkmsSdkClientFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public Client aliyunDkmsSdkClient(GeihouKmsProperties properties,
                                      GeihouKmsClientKeyPasswordResolver passwordResolver,
                                      AliyunDkmsSdkClientFactory sdkClientFactory) {
        String clientKeyPassword = passwordResolver.resolve(properties);
        Config config = GeihouKmsOpenApiConfigFactory.create(properties, clientKeyPassword, USER_AGENT);
        return sdkClientFactory.create(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public GeihouKmsSecretClient geihouKmsSecretClient(Client sdkClient) {
        return new AliyunDkmsSecretClientAdapter(sdkClient);
    }

    @Bean
    @ConditionalOnMissingBean(GeihouSigningSecretProvider.class)
    public GeihouSigningSecretProvider geihouKmsSigningSecretProvider(GeihouKmsProperties properties,
                                                                      GeihouKmsSecretNameMapper secretNameMapper,
                                                                      GeihouKmsSecretClient secretClient,
                                                                      GeihouKmsSigningSecretDecoder decoder) {
        return new GeihouKmsSigningSecretProvider(properties, secretNameMapper, secretClient, decoder);
    }

    @Bean
    @ConditionalOnMissingBean(GeihouActiveKidProvider.class)
    public GeihouActiveKidProvider geihouKmsActiveKidProvider(GeihouKmsProperties properties,
                                                              GeihouKmsSecretNameMapper secretNameMapper,
                                                              GeihouKmsSecretClient secretClient) {
        return new GeihouKmsActiveKidProvider(properties, secretNameMapper, secretClient);
    }
}
