package com.geihou.module.system.service.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring-facing binder for {@code geihou.security.two-factor} configuration.
 *
 * <p>H157S-M: binds the 2FA AES key source selection without holding any real
 * key material. The actual key bytes are read at runtime from either an
 * environment variable (ENV mode) or a KMS secret (KMS mode).
 */
@ConfigurationProperties(prefix = "geihou.security.two-factor")
public final class GeihouTwoFactorSpringProperties {

    private TwoFactorKeySource keySource;
    private String aesKeyEnv;
    private Kms kms = new Kms();

    /**
     * Key source selector for the 2FA AES key provider.
     */
    public enum TwoFactorKeySource {
        ENV, KMS
    }

    /**
     * Nested KMS-specific configuration.
     */
    public static final class Kms {
        private String secretName;

        public String getSecretName() {
            return secretName;
        }

        public void setSecretName(String secretName) {
            this.secretName = secretName;
        }
    }

    public TwoFactorKeySource getKeySource() {
        return keySource;
    }

    public void setKeySource(TwoFactorKeySource keySource) {
        this.keySource = keySource;
    }

    public String getAesKeyEnv() {
        return aesKeyEnv;
    }

    public void setAesKeyEnv(String aesKeyEnv) {
        this.aesKeyEnv = aesKeyEnv;
    }

    public Kms getKms() {
        return kms;
    }

    public void setKms(Kms kms) {
        this.kms = kms;
    }
}
