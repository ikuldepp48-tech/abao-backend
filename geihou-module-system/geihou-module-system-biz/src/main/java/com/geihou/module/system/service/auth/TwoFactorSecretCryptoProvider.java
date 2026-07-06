package com.geihou.module.system.service.auth;

public interface TwoFactorSecretCryptoProvider {

    String encrypt(String plaintextSecret);

    String decrypt(String encryptedSecret);
}
