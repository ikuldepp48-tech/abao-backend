package com.geihou.module.system.framework.jwt.kms;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring-facing binder adapter for immutable KMS domain properties.
 */
@ConfigurationProperties(prefix = "geihou.security.kms")
public final class GeihouKmsSpringProperties {

    private static final GeihouKmsProperties DEFAULTS = GeihouKmsProperties.disabledDefaults();

    private boolean enabled;
    private String endpoint;
    private String clientKeyFile;
    private String clientKeyPasswordEnv;
    private String caCertFile;
    private String secretNamePrefix = DEFAULTS.secretNamePrefix();
    private Duration connectTimeout = DEFAULTS.connectTimeout();
    private Duration readTimeout = DEFAULTS.readTimeout();
    private Duration cacheTtl = DEFAULTS.cacheTtl();
    private Duration staleCacheTtl = DEFAULTS.staleCacheTtl();

    public GeihouKmsProperties toDomainProperties() {
        return GeihouKmsProperties.of(
                enabled,
                endpoint,
                clientKeyFile,
                clientKeyPasswordEnv,
                caCertFile,
                secretNamePrefix,
                connectTimeout,
                readTimeout,
                cacheTtl,
                staleCacheTtl);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getClientKeyFile() {
        return clientKeyFile;
    }

    public void setClientKeyFile(String clientKeyFile) {
        this.clientKeyFile = clientKeyFile;
    }

    public String getClientKeyPasswordEnv() {
        return clientKeyPasswordEnv;
    }

    public void setClientKeyPasswordEnv(String clientKeyPasswordEnv) {
        this.clientKeyPasswordEnv = clientKeyPasswordEnv;
    }

    public String getCaCertFile() {
        return caCertFile;
    }

    public void setCaCertFile(String caCertFile) {
        this.caCertFile = caCertFile;
    }

    public String getSecretNamePrefix() {
        return secretNamePrefix;
    }

    public void setSecretNamePrefix(String secretNamePrefix) {
        this.secretNamePrefix = secretNamePrefix;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public Duration getStaleCacheTtl() {
        return staleCacheTtl;
    }

    public void setStaleCacheTtl(Duration staleCacheTtl) {
        this.staleCacheTtl = staleCacheTtl;
    }
}
