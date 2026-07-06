package com.geihou.module.system.framework.jwt.kms;

import java.util.function.Function;

/**
 * Resolves the ClientKey password from an injected source without reading the environment directly.
 */
public final class GeihouKmsClientKeyPasswordResolver {

    private final Function<String, String> passwordSource;

    public GeihouKmsClientKeyPasswordResolver(Function<String, String> passwordSource) {
        if (passwordSource == null) {
            throw new IllegalArgumentException("password-source must not be null");
        }
        this.passwordSource = passwordSource;
    }

    public String resolve(GeihouKmsProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("kms properties must not be null");
        }
        if (!properties.enabled()) {
            throw new GeihouKmsCredentialException("KMS ClientKey password is unavailable");
        }
        try {
            String password = passwordSource.apply(properties.clientKeyPasswordEnv());
            if (password == null || password.isBlank()) {
                throw new GeihouKmsCredentialException("KMS ClientKey password is unavailable");
            }
            return password;
        } catch (GeihouKmsCredentialException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new GeihouKmsCredentialException("KMS ClientKey password resolution failed");
        }
    }
}
