package com.geihou.framework.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for Geihou request security path policies.
 */
@ConfigurationProperties(prefix = "geihou.security")
public class GeihouSecurityProperties {

    private List<String> protectedPathPatterns = new ArrayList<>(List.of("/admin-api/**"));

    private List<String> permitPathPatterns = new ArrayList<>(List.of("/admin-api/auth/**"));

    public List<String> getProtectedPathPatterns() {
        return Collections.unmodifiableList(protectedPathPatterns);
    }

    public void setProtectedPathPatterns(List<String> protectedPathPatterns) {
        this.protectedPathPatterns = protectedPathPatterns == null
                ? new ArrayList<>()
                : new ArrayList<>(protectedPathPatterns);
    }

    public List<String> getPermitPathPatterns() {
        return Collections.unmodifiableList(permitPathPatterns);
    }

    public void setPermitPathPatterns(List<String> permitPathPatterns) {
        this.permitPathPatterns = permitPathPatterns == null
                ? new ArrayList<>()
                : new ArrayList<>(permitPathPatterns);
    }
}
