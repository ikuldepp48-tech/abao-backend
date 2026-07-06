package com.geihou.framework.security.core.constant;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class GeihouAuthErrorCodesTest {

    @Test
    void constantsShouldUseDecimalValuesFromPrd005() {
        assertThat(GeihouAuthErrorCodes.UNAUTHORIZED).isEqualTo(2);
        assertThat(GeihouAuthErrorCodes.FORBIDDEN).isEqualTo(3);
        assertThat(GeihouAuthErrorCodes.INVALID_TOKEN).isEqualTo(1001);
        assertThat(GeihouAuthErrorCodes.TOKEN_EXPIRED).isEqualTo(1002);
        assertThat(GeihouAuthErrorCodes.TOKEN_REVOKED).isEqualTo(1003);
        assertThat(GeihouAuthErrorCodes.LOGIN_FAILED).isEqualTo(1004);
        assertThat(GeihouAuthErrorCodes.ACCOUNT_LOCKED).isEqualTo(1005);
        assertThat(GeihouAuthErrorCodes.ACCOUNT_DISABLED).isEqualTo(1006);
        assertThat(GeihouAuthErrorCodes.TWO_FACTOR_REQUIRED).isEqualTo(1007);
    }

    @Test
    void shouldExposeExactlyNineAuthConstants() {
        long constantCount = Arrays.stream(GeihouAuthErrorCodes.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> Modifier.isFinal(field.getModifiers()))
                .count();

        assertThat(constantCount).isEqualTo(9);
    }
}
