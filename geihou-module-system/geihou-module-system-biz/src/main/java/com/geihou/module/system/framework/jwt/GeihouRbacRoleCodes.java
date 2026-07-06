package com.geihou.module.system.framework.jwt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * PRD 0-05 raw RBAC role-code contract for JWT roles.
 */
public final class GeihouRbacRoleCodes {

    private static final int MAX_LENGTH = 64;
    private static final String FRAMEWORK_PREFIX = "ROLE_";
    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*");

    private GeihouRbacRoleCodes() {
    }

    public static List<String> normalize(List<String> roleCodes) {
        Objects.requireNonNull(roleCodes, "roles must not be null");
        if (roleCodes.isEmpty()) {
            throw new IllegalArgumentException("roles must not be empty");
        }

        Set<String> uniqueRoleCodes = new HashSet<>();
        for (String roleCode : roleCodes) {
            validate(roleCode);
            if (!uniqueRoleCodes.add(roleCode)) {
                throw new IllegalArgumentException("roles must not contain duplicates");
            }
        }

        List<String> normalized = new ArrayList<>(uniqueRoleCodes);
        normalized.sort(String::compareTo);
        return List.copyOf(normalized);
    }

    private static void validate(String roleCode) {
        Objects.requireNonNull(roleCode, "roles must not contain null entries");
        if (roleCode.isBlank()) {
            throw new IllegalArgumentException("roles must not contain blank entries");
        }
        if (roleCode.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("role code must not exceed 64 characters");
        }
        if (roleCode.startsWith(FRAMEWORK_PREFIX)) {
            throw new IllegalArgumentException("role code must not use the ROLE_ framework prefix");
        }
        if (!ROLE_CODE_PATTERN.matcher(roleCode).matches()) {
            throw new IllegalArgumentException("role code must be uppercase snake case");
        }
    }
}
