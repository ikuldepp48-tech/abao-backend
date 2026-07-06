package com.geihou.common.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    void shouldCreateErrorCode() {
        ErrorCode errorCode = new ErrorCode(100001, "code already exists");

        assertEquals(100001, errorCode.getCode());
        assertEquals("code already exists", errorCode.getMsg());
    }

    @Test
    void shouldRejectNullCode() {
        assertThrows(NullPointerException.class, () -> new ErrorCode(null, "failed"));
    }

    @Test
    void shouldRejectNullMsg() {
        assertThrows(IllegalArgumentException.class, () -> new ErrorCode(100001, null));
    }

    @Test
    void shouldRejectBlankMsg() {
        assertThrows(IllegalArgumentException.class, () -> new ErrorCode(100001, " "));
    }

    @Test
    void shouldUseCodeIdentityForEquality() {
        ErrorCode first = new ErrorCode(100001, "first");
        ErrorCode second = new ErrorCode(100001, "second");
        ErrorCode other = new ErrorCode(100002, "first");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, other);
    }

    @Test
    void toStringShouldContainCodeAndMsg() {
        ErrorCode errorCode = new ErrorCode(100001, "code already exists");

        assertTrue(errorCode.toString().contains("100001"));
        assertTrue(errorCode.toString().contains("code already exists"));
    }
}
