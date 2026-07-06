package com.geihou.common.pojo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CommonResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void successShouldPreserveData() {
        CommonResult<String> result = CommonResult.success("ok");

        assertEquals(0, result.getCode());
        assertEquals("success", result.getMsg());
        assertEquals("ok", result.getData());
    }

    @Test
    void successShouldAllowNullData() {
        CommonResult<Void> result = CommonResult.success(null);

        assertEquals(0, result.getCode());
        assertEquals("success", result.getMsg());
        assertNull(result.getData());
    }

    @Test
    void errorShouldRejectNullCode() {
        assertThrows(NullPointerException.class, () -> CommonResult.error(null, "failed"));
    }

    @Test
    void errorShouldRejectSuccessCode() {
        assertThrows(IllegalArgumentException.class, () -> CommonResult.error(0, "failed"));
    }

    @Test
    void errorShouldRejectNullMsg() {
        assertThrows(IllegalArgumentException.class, () -> CommonResult.error(100001, null));
    }

    @Test
    void errorShouldRejectBlankMsg() {
        assertThrows(IllegalArgumentException.class, () -> CommonResult.error(100001, " "));
    }

    @Test
    void errorShouldCreateErrorResult() {
        CommonResult<String> result = CommonResult.error(100001, "failed");

        assertEquals(100001, result.getCode());
        assertEquals("failed", result.getMsg());
        assertNull(result.getData());
    }

    @Test
    void shouldSerializeOnlyCodeMsgAndData() throws Exception {
        String json = objectMapper.writeValueAsString(CommonResult.success("ok"));
        JsonNode node = objectMapper.readTree(json);

        assertTrue(node.has("code"));
        assertTrue(node.has("msg"));
        assertTrue(node.has("data"));
        assertFalse(node.has("timestamp"));
        assertFalse(node.has("traceId"));
        assertEquals(0, node.get("code").asInt());
        assertEquals("success", node.get("msg").asText());
        assertEquals("ok", node.get("data").asText());
    }
}
