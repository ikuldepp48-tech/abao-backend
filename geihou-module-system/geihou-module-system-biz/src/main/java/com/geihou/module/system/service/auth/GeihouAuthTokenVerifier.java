package com.geihou.module.system.service.auth;

import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.module.system.api.auth.dto.AuthTokenVerifyRespDTO;
import com.geihou.module.system.dal.dataobject.auth.AuthUserDO;
import com.geihou.module.system.dal.mysql.auth.AuthTokenRevokedRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import com.geihou.module.system.framework.jwt.GeihouJwtHeader;
import com.geihou.module.system.framework.jwt.GeihouJwtHeaderParser;
import com.geihou.module.system.framework.jwt.GeihouJwtTokenParser;
import com.geihou.module.system.framework.jwt.GeihouSigningSecret;
import com.geihou.module.system.framework.jwt.GeihouSigningSecretProvider;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure Java token verification composer. Runtime registration is intentionally out of scope.
 */
public class GeihouAuthTokenVerifier {

    private static final String INVALID_TOKEN_MESSAGE = "Token is invalid";
    private static final String TOKEN_REVOKED_MESSAGE = "Token revoked";
    private static final String ACCOUNT_LOCKED_MESSAGE = "Account locked";
    private static final String ACCOUNT_DISABLED_MESSAGE = "Account disabled";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_LOCKED = "LOCKED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final GeihouJwtHeaderParser headerParser;
    private final GeihouSigningSecretProvider signingSecretProvider;
    private final GeihouJwtTokenParser jwtTokenParser;
    private final AuthTokenRevokedRepository revokedRepository;
    private final AuthUserRepository userRepository;

    public GeihouAuthTokenVerifier(GeihouJwtHeaderParser headerParser,
                                   GeihouSigningSecretProvider signingSecretProvider,
                                   GeihouJwtTokenParser jwtTokenParser,
                                   AuthTokenRevokedRepository revokedRepository,
                                   AuthUserRepository userRepository) {
        this.headerParser = Objects.requireNonNull(headerParser, "headerParser must not be null");
        this.signingSecretProvider = Objects.requireNonNull(signingSecretProvider,
                "signingSecretProvider must not be null");
        this.jwtTokenParser = Objects.requireNonNull(jwtTokenParser, "jwtTokenParser must not be null");
        this.revokedRepository = Objects.requireNonNull(revokedRepository, "revokedRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    }

    public AuthTokenVerifyRespDTO verify(String token) {
        Optional<GeihouJwtHeader> header = headerParser.parse(token);
        if (header.isEmpty()) {
            return invalid();
        }

        Optional<GeihouSigningSecret> secret = resolveSecret(header.get().kid());
        if (secret.isEmpty()) {
            return invalid();
        }

        AuthTokenVerifyRespDTO verified = jwtTokenParser.verifyToken(token, secret.get().keyBytes());
        if (!Boolean.TRUE.equals(verified.getValid())) {
            return verified;
        }

        try {
            if (isBlank(verified.getTokenId())) {
                return invalid();
            }
            if (revokedRepository.existsByJti(verified.getTokenId())) {
                return error(GeihouAuthErrorCodes.TOKEN_REVOKED, TOKEN_REVOKED_MESSAGE);
            }
            AuthUserDO user = userRepository.selectById(verified.getUserId());
            return verifyAccountState(verified, user);
        } catch (RuntimeException ex) {
            return invalid();
        }
    }

    private Optional<GeihouSigningSecret> resolveSecret(String kid) {
        try {
            return signingSecretProvider.resolve(kid);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static AuthTokenVerifyRespDTO verifyAccountState(AuthTokenVerifyRespDTO verified, AuthUserDO user) {
        if (user == null
                || !Objects.equals(user.getId(), verified.getUserId())
                || !Objects.equals(user.getTenantId(), verified.getTenantId())
                || !Objects.equals(user.getUserRole(), verified.getUserRole())) {
            return invalid();
        }
        String status = user.getStatus();
        if (STATUS_ACTIVE.equals(status)) {
            return verified;
        }
        if (STATUS_LOCKED.equals(status)) {
            return error(GeihouAuthErrorCodes.ACCOUNT_LOCKED, ACCOUNT_LOCKED_MESSAGE);
        }
        if (STATUS_DISABLED.equals(status)) {
            return error(GeihouAuthErrorCodes.ACCOUNT_DISABLED, ACCOUNT_DISABLED_MESSAGE);
        }
        return invalid();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static AuthTokenVerifyRespDTO invalid() {
        return error(GeihouAuthErrorCodes.INVALID_TOKEN, INVALID_TOKEN_MESSAGE);
    }

    private static AuthTokenVerifyRespDTO error(Integer code, String message) {
        AuthTokenVerifyRespDTO response = new AuthTokenVerifyRespDTO();
        response.setValid(false);
        response.setErrorCode(code);
        response.setErrorMessage(message);
        return response;
    }
}
