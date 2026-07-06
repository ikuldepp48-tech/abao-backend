package com.geihou.framework.security.core.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geihou.common.pojo.CommonResult;
import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.framework.security.core.exception.GeihouAuthException;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes Geihou auth errors as CommonResult JSON.
 */
public class GeihouSecurityErrorHandler {

    private final ObjectMapper objectMapper;

    public GeihouSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void handle(HttpServletResponse response, GeihouAuthException exception) throws IOException {
        handle(response, exception.getErrorCode(), exception.getMessage());
    }

    public void handle(HttpServletResponse response, int errorCode, String message) throws IOException {
        response.setStatus(httpStatus(errorCode));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), CommonResult.error(errorCode, message));
    }

    private int httpStatus(int errorCode) {
        return errorCode == GeihouAuthErrorCodes.FORBIDDEN
                ? HttpServletResponse.SC_FORBIDDEN
                : HttpServletResponse.SC_UNAUTHORIZED;
    }
}
