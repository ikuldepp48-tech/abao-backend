package com.geihou.module.system.dal.mysql.auth;

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
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthTokenRevokedRepositoryGuardTest {

    private static final Set<String> ALLOWED_PUBLIC_METHODS = Set.of(
            "insert",
            "selectByJti",
            "selectByUserId",
            "existsByJti",
            "deleteExpired"
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

    @Test
    void repositoryPublicMethodsMustBeAllowed() {
        for (Method method : AuthTokenRevokedRepository.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            String name = method.getName();
            assertThat(ALLOWED_PUBLIC_METHODS)
                    .as("Repository public method '%s' must stay inside H61 allowed facade", name)
                    .contains(name);
        }
    }

    @Test
    void onlyMapperAndRepositoryMayReferenceAuthTokenRevokedMapper() throws IOException {
        Set<String> allowedFiles = Set.of("AuthTokenRevokedMapper.java", "AuthTokenRevokedRepository.java");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            List<Path> javaFiles = walk
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path javaFile : javaFiles) {
                String fileName = javaFile.getFileName().toString();
                if (allowedFiles.contains(fileName)) {
                    continue;
                }
                String content = stripJavaComments(Files.readString(javaFile));
                if (content.contains("AuthTokenRevokedMapper")) {
                    violations.add(sourceRoot.relativize(javaFile).toString());
                }
            }
        }

        assertThat(violations)
                .as("Main-code direct AuthTokenRevokedMapper references must go through the repository")
                .isEmpty();
    }

    @Test
    void repositoryMustNotCallForbiddenMapperMutatorMethods() throws IOException {
        String content = stripJavaComments(Files.readString(repositorySourcePath()));

        assertThat(content).doesNotContain("mapper.update");
        assertThat(content).doesNotContain("mapper.insertOrUpdate");
    }

    @Test
    void repositoryMayCallMapperDeleteOnlyForExpiredRows() throws IOException {
        String content = stripJavaComments(Files.readString(repositorySourcePath()));
        String deleteExpiredBody = extractMethodBody(content, "public int deleteExpired(LocalDateTime before)");
        String contentWithoutDeleteExpired = content.replace(deleteExpiredBody, "");

        assertThat(contentWithoutDeleteExpired)
                .as("mapper.delete must not appear outside deleteExpired")
                .doesNotContain("mapper.delete");
        assertThat(deleteExpiredBody).contains("mapper.delete(new LambdaQueryWrapper<AuthTokenRevokedDO>()");
        assertThat(deleteExpiredBody).contains(".lt(AuthTokenRevokedDO::getExpireTime, before)");
    }

    @Test
    void doMustNotContainForbiddenFieldsOrTableLogic() throws IOException {
        Path doPath = sourceRoot.resolve(
                "com/geihou/module/system/dal/dataobject/auth/AuthTokenRevokedDO.java");

        assertThat(doPath).exists();

        String content = stripJavaComments(Files.readString(doPath));
        assertThat(content).doesNotContain("@TableLogic");
        assertThat(content).doesNotContainPattern(fieldDeclarationPattern("deleted"));
        assertThat(content).doesNotContainPattern(fieldDeclarationPattern("tenantId"));
        assertThat(content).doesNotContainPattern(fieldDeclarationPattern("creator"));
        assertThat(content).doesNotContainPattern(fieldDeclarationPattern("updater"));
        assertThat(content).doesNotContainPattern(fieldDeclarationPattern("updateTime"));
    }

    private static Path repositorySourcePath() {
        Path repoPath = sourceRoot.resolve(
                "com/geihou/module/system/dal/mysql/auth/AuthTokenRevokedRepository.java");
        assertThat(repoPath).exists();
        return repoPath;
    }

    private static String extractMethodBody(String content, String signature) {
        int signatureStart = content.indexOf(signature);
        assertThat(signatureStart)
                .as("Repository must contain method signature: %s", signature)
                .isGreaterThanOrEqualTo(0);

        int bodyStart = content.indexOf('{', signatureStart);
        assertThat(bodyStart).isGreaterThanOrEqualTo(0);

        int depth = 0;
        for (int i = bodyStart; i < content.length(); i++) {
            char current = content.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return content.substring(signatureStart, i + 1);
                }
            }
        }
        throw new IllegalStateException("Cannot find method body for " + signature);
    }

    private static String stripJavaComments(String content) {
        return content
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }

    private static Pattern fieldDeclarationPattern(String fieldName) {
        return Pattern.compile("\\bprivate\\s+[^;=]+\\s+" + Pattern.quote(fieldName) + "\\b");
    }
}
