package com.geihou.module.system.service.auth;

public interface TwoFactorSecretKeyProvider {

    byte[] currentKey();
}
