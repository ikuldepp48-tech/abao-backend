package com.geihou.module.system.framework.security.config;

import com.geihou.framework.security.config.GeihouSecurityAutoConfiguration;
import com.geihou.framework.security.core.spi.TokenValidator;
import com.geihou.module.system.api.auth.AuthApi;
import com.geihou.module.system.dal.mysql.auth.AuthTokenRevokedRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import com.geihou.module.system.framework.jwt.GeihouJwtHeaderParser;
import com.geihou.module.system.framework.jwt.GeihouJwtTokenParser;
import com.geihou.module.system.framework.jwt.GeihouSigningSecretProvider;
import com.geihou.module.system.service.auth.AuthApiImpl;
import com.geihou.module.system.service.auth.AuthApiTokenValidator;
import com.geihou.module.system.service.auth.GeihouAuthTokenVerifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Conditional wiring for the Geihou auth verification chain.
 */
@AutoConfiguration(before = GeihouSecurityAutoConfiguration.class)
@ConditionalOnBean(GeihouSigningSecretProvider.class)
public class GeihouAuthTokenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GeihouJwtHeaderParser.class)
    public GeihouJwtHeaderParser geihouJwtHeaderParser() {
        return new GeihouJwtHeaderParser();
    }

    @Bean
    @ConditionalOnMissingBean(GeihouJwtTokenParser.class)
    public GeihouJwtTokenParser geihouJwtTokenParser() {
        return new GeihouJwtTokenParser();
    }

    @Bean
    @ConditionalOnMissingBean(GeihouAuthTokenVerifier.class)
    public GeihouAuthTokenVerifier geihouAuthTokenVerifier(GeihouJwtHeaderParser headerParser,
                                                           GeihouSigningSecretProvider signingSecretProvider,
                                                           GeihouJwtTokenParser jwtTokenParser,
                                                           AuthTokenRevokedRepository revokedRepository,
                                                           AuthUserRepository userRepository) {
        return new GeihouAuthTokenVerifier(
                headerParser, signingSecretProvider, jwtTokenParser, revokedRepository, userRepository);
    }

    @Bean
    @ConditionalOnMissingBean(AuthApi.class)
    public AuthApi authApi(GeihouAuthTokenVerifier tokenVerifier) {
        return new AuthApiImpl(tokenVerifier);
    }

    @Bean
    @ConditionalOnMissingBean(TokenValidator.class)
    public TokenValidator authApiTokenValidator(AuthApi authApi) {
        return new AuthApiTokenValidator(authApi);
    }
}
