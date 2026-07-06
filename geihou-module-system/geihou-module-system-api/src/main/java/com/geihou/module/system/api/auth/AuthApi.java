package com.geihou.module.system.api.auth;

import com.geihou.module.system.api.auth.dto.AuthTokenVerifyRespDTO;

/**
 * Auth API contract used by Geihou modules.
 *
 * <p>This is a pure Java contract in the first auth compile slice. RPC
 * annotations, implementation, token issuance, permission checks, role checks,
 * and token revocation are intentionally out of scope.</p>
 */
public interface AuthApi {

    /**
     * Verifies a raw token and returns structured verification data.
     *
     * @param token raw token string without the Bearer prefix
     * @return token verification DTO, never a security-starter SPI type
     */
    AuthTokenVerifyRespDTO verifyToken(String token);
}
