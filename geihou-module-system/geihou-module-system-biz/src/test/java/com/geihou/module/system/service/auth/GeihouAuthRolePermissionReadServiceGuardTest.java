package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.geihou.module.system.dal.mysql.auth.AuthPermissionRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRolePermissionRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRoleRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRoleRepository;

class GeihouAuthRolePermissionReadServiceGuardTest {

    private static final Set<String> ALLOWED_SERVICE_PUBLIC_METHODS = Set.of(
            "resolveActiveRoleCodes",
            "resolveAssignedPermissionCodes"
    );

    private static final Set<String> ALLOWED_USER_ROLE_REPO_METHODS = Set.of(
            "selectActiveByTenantIdAndUserId"
    );

    private static final Set<String> ALLOWED_ROLE_REPO_METHODS = Set.of(
            "selectActiveByTenantIdAndIds"
    );

    private static final Set<String> ALLOWED_ROLE_PERMISSION_REPO_METHODS = Set.of(
            "selectActiveByTenantIdAndRoleIds"
    );

    private static final Set<String> ALLOWED_PERMISSION_REPO_METHODS = Set.of(
            "selectActiveByIds"
    );

    private static Path sourceRoot;

    @BeforeAll
    static void resolveSourceRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path candidate = cwd.resolve("src/main/java");
        if (Files.isDirectory(candidate)) {
            sourceRoot = candidate;
            return;
        }
        candidate = cwd.resolve("../geihou-module-system/geihou-module-system-biz/src/main/java").normalize();
        if (Files.isDirectory(candidate)) {
            sourceRoot = candidate;
            return;
        }
        throw new IllegalStateException("Cannot resolve src/main/java directory. cwd=" + cwd);
    }

    // ---- T-12: No permissions in JWT ----

    @Test
    void jwtIssuerMustNotContainPermissionClaims() throws IOException {
        Path jwtDir = sourceRoot.resolve("com/geihou/module/system/framework/jwt");
        assertThat(Files.isDirectory(jwtDir)).isTrue();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(jwtDir)) {
            List<Path> javaFiles = walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
            for (Path javaFile : javaFiles) {
                // Comments are stripped first, so comments stating permissions are excluded
                // are inherently allowed. H146 repository classes (AuthPermissionRepository,
                // AuthPermissionDO, etc.) live outside the jwt/ directory and are not scanned.
                String content = stripComments(Files.readString(javaFile));
                if (containsPermissionClaimConstruction(content)) {
                    violations.add(sourceRoot.relativize(javaFile).toString());
                }
            }
        }
        assertThat(violations)
                .as("JWT framework code must not construct or access permission claims; "
                        + "permissions never enter JWT (PRD 0-05)")
                .isEmpty();
    }

    /**
     * Explicit scan for actual permission-related JWT claim construction or access.
     *
     * <p>Fails for patterns such as:
     * <ul>
     *   <li>{@code .claim("permissions", ...)} or {@code .claim("permission", ...)} — claim construction</li>
     *   <li>{@code .getStringListClaim("permissions")} — claim access via string list</li>
     *   <li>{@code .getClaim("permissions")} — generic claim access</li>
     *   <li>String literals {@code "permission"} or {@code "permissions"} — claim name constants
     *       that would serialize permission(s) into JWT</li>
     * </ul>
     *
     * <p>Comments are already stripped before this method is called, so comments stating
     * "permissions are excluded" or "permissions never JWT" are inherently allowed.
     * H146 repository classes are outside the scanned jwt/ directory.
     */
    private static boolean containsPermissionClaimConstruction(String content) {
        // 1. Detect .claim("permission..."), .getStringListClaim("permission..."),
        //    .getClaim("permission...") — actual claim construction/access with a
        //    permission-related key.
        java.util.regex.Pattern claimMethodPattern = java.util.regex.Pattern.compile(
                "\\.(claim|getStringListClaim|getClaim)\\s*\\(\\s*\"permission[^\"]*\"",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        if (claimMethodPattern.matcher(content).find()) {
            return true;
        }
        // 2. Detect string literals "permission" or "permissions" that would define a
        //    claim name constant or be used to serialize permission(s) into JWT.
        //    This catches constants like: String PERMISSIONS = "permissions";
        java.util.regex.Pattern permissionStringLiteral = java.util.regex.Pattern.compile(
                "\"permission(s)?\"");
        if (permissionStringLiteral.matcher(content).find()) {
            return true;
        }
        return false;
    }

    // ---- T-13: No writes in repositories ----

    @Test
    void authRoleRepositoryMustBeReadOnly() throws IOException {
        String content = stripComments(Files.readString(sourceRoot.resolve(
                "com/geihou/module/system/dal/mysql/auth/AuthRoleRepository.java")));
        assertThat(content).doesNotContain("mapper.insert");
        assertThat(content).doesNotContain("mapper.update");
        assertThat(content).doesNotContain("mapper.delete");
        assertThat(content).doesNotContain("mapper.insertOrUpdate");
    }

    @Test
    void authUserRoleRepositoryMustBeReadOnly() throws IOException {
        String content = stripComments(Files.readString(sourceRoot.resolve(
                "com/geihou/module/system/dal/mysql/auth/AuthUserRoleRepository.java")));
        assertThat(content).doesNotContain("mapper.insert");
        assertThat(content).doesNotContain("mapper.update");
        assertThat(content).doesNotContain("mapper.delete");
        assertThat(content).doesNotContain("mapper.insertOrUpdate");
    }

    @Test
    void authPermissionRepositoryMustBeReadOnly() throws IOException {
        String content = stripComments(Files.readString(sourceRoot.resolve(
                "com/geihou/module/system/dal/mysql/auth/AuthPermissionRepository.java")));
        assertThat(content).doesNotContain("mapper.insert");
        assertThat(content).doesNotContain("mapper.update");
        assertThat(content).doesNotContain("mapper.delete");
        assertThat(content).doesNotContain("mapper.insertOrUpdate");
    }

    @Test
    void authRolePermissionRepositoryMustBeReadOnly() throws IOException {
        String content = stripComments(Files.readString(sourceRoot.resolve(
                "com/geihou/module/system/dal/mysql/auth/AuthRolePermissionRepository.java")));
        assertThat(content).doesNotContain("mapper.insert");
        assertThat(content).doesNotContain("mapper.update");
        assertThat(content).doesNotContain("mapper.delete");
        assertThat(content).doesNotContain("mapper.insertOrUpdate");
    }

    // ---- Exact public method enforcement ----

    @Test
    void readServiceMustHaveExactlyTwoPublicMethods() {
        Set<String> publicMethods = collectPublicMethodNames(GeihouAuthRolePermissionReadService.class);
        assertThat(publicMethods)
                .as("Read service must expose exactly the two allowed public methods")
                .isEqualTo(ALLOWED_SERVICE_PUBLIC_METHODS);
    }

    @Test
    void authUserRoleRepositoryMustHaveExactlyOnePublicMethod() {
        Set<String> publicMethods = collectPublicMethodNames(AuthUserRoleRepository.class);
        assertThat(publicMethods)
                .as("AuthUserRoleRepository must expose exactly selectActiveByTenantIdAndUserId")
                .isEqualTo(ALLOWED_USER_ROLE_REPO_METHODS);
    }

    @Test
    void authRoleRepositoryMustHaveExactlyOnePublicMethod() {
        Set<String> publicMethods = collectPublicMethodNames(AuthRoleRepository.class);
        assertThat(publicMethods)
                .as("AuthRoleRepository must expose exactly selectActiveByTenantIdAndIds")
                .isEqualTo(ALLOWED_ROLE_REPO_METHODS);
    }

    @Test
    void authRolePermissionRepositoryMustHaveExactlyOnePublicMethod() {
        Set<String> publicMethods = collectPublicMethodNames(AuthRolePermissionRepository.class);
        assertThat(publicMethods)
                .as("AuthRolePermissionRepository must expose exactly selectActiveByTenantIdAndRoleIds")
                .isEqualTo(ALLOWED_ROLE_PERMISSION_REPO_METHODS);
    }

    @Test
    void authPermissionRepositoryMustHaveExactlyOnePublicMethod() {
        Set<String> publicMethods = collectPublicMethodNames(AuthPermissionRepository.class);
        assertThat(publicMethods)
                .as("AuthPermissionRepository must expose exactly selectActiveByIds")
                .isEqualTo(ALLOWED_PERMISSION_REPO_METHODS);
    }

    // ---- No /auth/me, no controller, no cache, no VO ----

    @Test
    void readServiceMustNotBeControllerOrApi() {
        assertThat(GeihouAuthRolePermissionReadService.class.getAnnotations())
                .as("Read service must not have Spring annotations (no @RestController/@Controller/@Service)")
                .isEmpty();
    }

    @Test
    void readServiceMustNotReferenceAuthMeOrCacheOrVo() throws IOException {
        String content = stripComments(Files.readString(sourceRoot.resolve(
                "com/geihou/module/system/service/auth/GeihouAuthRolePermissionReadService.java")));
        String lower = content.toLowerCase();
        assertThat(lower).doesNotContain("auth/me");
        assertThat(lower).doesNotContain("cache");
        assertThat(lower).doesNotContain("restcontroller");
        assertThat(lower).doesNotContain("controller");
        assertThat(lower).doesNotContain("requestmapping");
    }

    // ---- DOs must not use @TableLogic ----

    @Test
    void authRoleDoMustNotUseTableLogic() throws IOException {
        assertNoTableLogic("AuthRoleDO");
    }

    @Test
    void authUserRoleDoMustNotUseTableLogic() throws IOException {
        assertNoTableLogic("AuthUserRoleDO");
    }

    @Test
    void authPermissionDoMustNotUseTableLogic() throws IOException {
        assertNoTableLogic("AuthPermissionDO");
    }

    @Test
    void authRolePermissionDoMustNotUseTableLogic() throws IOException {
        assertNoTableLogic("AuthRolePermissionDO");
    }

    @Test
    void authPermissionDoMustNotHaveTenantId() throws IOException {
        Path doPath = sourceRoot.resolve(
                "com/geihou/module/system/dal/dataobject/auth/AuthPermissionDO.java");
        String content = stripComments(Files.readString(doPath));
        assertThat(content).doesNotContain("@TableField(\"tenant_id\")");
        assertThat(content).doesNotContainPattern("\\bprivate\\s+[^;=]+\\s+tenantId\\b");
    }

    @Test
    void mappersMustBeBareBaseMapperX() throws IOException {
        String[] mapperNames = {"AuthRoleMapper", "AuthUserRoleMapper", "AuthPermissionMapper",
                "AuthRolePermissionMapper"};
        for (String mapperName : mapperNames) {
            Path mapperPath = sourceRoot.resolve(
                    "com/geihou/module/system/dal/mysql/auth/" + mapperName + ".java");
            String content = stripComments(Files.readString(mapperPath));
            assertThat(content)
                    .as("%s must extend BaseMapperX", mapperName)
                    .contains("BaseMapperX");
            assertThat(content)
                    .as("%s must be annotated @Mapper", mapperName)
                    .contains("@Mapper");
            // Verify the interface body contains no declarations (no methods, no fields,
            // no constants). The body between the first '{' and last '}' must be empty or
            // whitespace-only after stripping comments.
            int firstBrace = content.indexOf('{');
            int lastBrace = content.lastIndexOf('}');
            assertThat(firstBrace)
                    .as("%s must contain an opening brace", mapperName)
                    .isGreaterThan(-1);
            assertThat(lastBrace)
                    .as("%s must contain a closing brace", mapperName)
                    .isGreaterThan(firstBrace);
            String body = content.substring(firstBrace + 1, lastBrace).trim();
            assertThat(body)
                    .as("%s interface body must be empty (bare BaseMapperX, no custom declarations)",
                            mapperName)
                    .isEmpty();
        }
    }

    // ---- Helper methods ----

    private static void assertNoTableLogic(String doClassName) throws IOException {
        Path doPath = sourceRoot.resolve(
                "com/geihou/module/system/dal/dataobject/auth/" + doClassName + ".java");
        assertThat(doPath).exists();
        String content = stripComments(Files.readString(doPath));
        assertThat(content).doesNotContain("@TableLogic");
        assertThat(content).contains("@TableField(\"deleted\")");
    }

    private static Set<String> collectPublicMethodNames(Class<?> clazz) {
        return Stream.of(clazz.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toSet());
    }

    private static String stripComments(String content) {
        return content
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }
}
