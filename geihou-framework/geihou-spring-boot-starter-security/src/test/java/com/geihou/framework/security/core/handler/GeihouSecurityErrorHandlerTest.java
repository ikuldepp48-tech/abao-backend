package com.geihou.framework.security.core.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.framework.security.core.exception.GeihouAuthException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeihouSecurityErrorHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final GeihouSecurityErrorHandler handler = new GeihouSecurityErrorHandler(objectMapper);

    @Test
    void shouldWriteUnauthorizedCommonResult() throws Exception {
        ResponseCapture response = responseCapture();

        handler.handle(response.response(), GeihouAuthErrorCodes.INVALID_TOKEN, "Token is invalid");

        verify(response.response()).setStatus(401);
        verify(response.response()).setContentType("application/json;charset=UTF-8");
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("code").asInt()).isEqualTo(GeihouAuthErrorCodes.INVALID_TOKEN);
        assertThat(body.get("msg").asText()).isEqualTo("Token is invalid");
        assertThat(body.get("data").isNull()).isTrue();
    }

    @Test
    void shouldWriteForbiddenCommonResult() throws Exception {
        ResponseCapture response = responseCapture();

        handler.handle(response.response(), new GeihouAuthException(GeihouAuthErrorCodes.FORBIDDEN, "Forbidden"));

        verify(response.response()).setStatus(403);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("code").asInt()).isEqualTo(GeihouAuthErrorCodes.FORBIDDEN);
        assertThat(body.get("msg").asText()).isEqualTo("Forbidden");
    }

    private ResponseCapture responseCapture() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));
        return new ResponseCapture(response, writer);
    }

    private record ResponseCapture(HttpServletResponse response, StringWriter writer) {

        String body() {
            return writer.toString();
        }
    }
}
