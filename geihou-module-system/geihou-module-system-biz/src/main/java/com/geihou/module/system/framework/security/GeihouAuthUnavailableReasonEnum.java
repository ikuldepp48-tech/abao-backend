package com.geihou.module.system.framework.security;

/**
 * Public reason strings returned in {@code CommonResult.msg} when the auth
 * subsystem is entirely unavailable (HTTP 503, code=1008).
 *
 * <p>Registered in the global enum table as
 * {@code ENUM_AUTH_SERVICE_UNAVAILABLE_REASON}. Distinct from
 * {@code ENUM_AUTH_DENIED_PUBLIC_REASON} (code=401000): 401000 means the auth
 * subsystem is running but credentials were rejected; 1008 means the auth
 * subsystem is entirely offline.
 *
 * <p>Source: G0-04H185-SYS-503-CONTRACT.
 */
public enum GeihouAuthUnavailableReasonEnum {

    AUTH_SERVICE_UNAVAILABLE
}
