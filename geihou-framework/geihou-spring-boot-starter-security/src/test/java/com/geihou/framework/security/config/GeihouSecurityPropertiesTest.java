package com.geihou.framework.security.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class GeihouSecurityPropertiesTest {

    @Test
    void shouldExposeDefaultPathPatterns() {
        GeihouSecurityProperties properties = new GeihouSecurityProperties();

        assertThat(properties.getProtectedPathPatterns()).containsExactly("/admin-api/**");
        assertThat(properties.getPermitPathPatterns()).containsExactly("/admin-api/auth/**");
    }

    @Test
    void shouldDefensivelyCopyProtectedPathPatterns() {
        GeihouSecurityProperties properties = new GeihouSecurityProperties();
        List<String> patterns = new ArrayList<>(List.of("/admin/**"));

        properties.setProtectedPathPatterns(patterns);
        patterns.add("/later/**");

        assertThat(properties.getProtectedPathPatterns()).containsExactly("/admin/**");
    }

    @Test
    void shouldDefensivelyCopyPermitPathPatterns() {
        GeihouSecurityProperties properties = new GeihouSecurityProperties();
        List<String> patterns = new ArrayList<>(List.of("/auth/**"));

        properties.setPermitPathPatterns(patterns);
        patterns.add("/later/**");

        assertThat(properties.getPermitPathPatterns()).containsExactly("/auth/**");
    }

    @Test
    void shouldNeverExposeNullLists() {
        GeihouSecurityProperties properties = new GeihouSecurityProperties();

        properties.setProtectedPathPatterns(null);
        properties.setPermitPathPatterns(null);

        assertThat(properties.getProtectedPathPatterns()).isEmpty();
        assertThat(properties.getPermitPathPatterns()).isEmpty();
    }

    @Test
    void shouldExposeUnmodifiableLists() {
        GeihouSecurityProperties properties = new GeihouSecurityProperties();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> properties.getProtectedPathPatterns().add("/boom/**"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> properties.getPermitPathPatterns().add("/boom/**"));
    }
}
