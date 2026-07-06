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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthUserRepositoryGuardTest {

    private static final Set<String> ALLOWED_PUBLIC_METHODS = Set.of(
            "insert",
            "selectById",
            "selectByTenantIdAndPhone",
            "selectByTenantIdAndUsername",
            "selectByTenantIdAndWechatOpenid",
            "existsByTenantIdAndPhone",
            "existsByTenantIdAndUsername",
            "existsByTenantIdAndWechatOpenid",
            "updateLoginFailureState",
            "resetLoginState"
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
        for (Method method : AuthUserRepository.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            String name = method.getName();
            assertThat(ALLOWED_PUBLIC_METHODS)
                    .as("Repository public method '%s' must stay inside H69 allowed facade", name)
                    .contains(name);
        }
    }

    @Test
    void onlyMapperAndRepositoryMayReferenceAuthUserMapper() throws IOException {
        Set<String> allowedFiles = Set.of("AuthUserMapper.java", "AuthUserRepository.java");
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
                if (content.contains("AuthUserMapper")) {
                    violations.add(sourceRoot.relativize(javaFile).toString());
                }
            }
        }

        assertThat(violations)
                .as("Main-code direct AuthUserMapper references must go through the repository")
                .isEmpty();
    }

    @Test
    void repositoryMustOnlyCallAllowedMapperMutatorMethods() throws IOException {
        String content = stripJavaComments(Files.readString(repositorySourcePath()));

        assertThat(content).doesNotContain("mapper.delete");
        assertThat(content).doesNotContain("mapper.insertOrUpdate");
        assertThat(content).contains("updateLoginFailureState");
        assertThat(content).contains("resetLoginState");
        assertThat(content.split("mapper.update", -1).length - 1).isEqualTo(2);
    }

    @Test
    void repositoryQueriesMustFilterDeletedRows() throws IOException {
        String content = stripJavaComments(Files.readString(repositorySourcePath()));

        assertThat(content).contains("eq(AuthUserDO::getDeleted, false)");
        assertThat(content).contains("mapper.selectOne(activeQuery()");
        assertThat(content).contains("mapper.selectCount(activeQuery()");
    }

    @Test
    void doMustMapPrdColumnsWithoutTableLogic() throws IOException {
        Path doPath = sourceRoot.resolve("com/geihou/module/system/dal/dataobject/auth/AuthUserDO.java");

        assertThat(doPath).exists();

        String content = stripJavaComments(Files.readString(doPath));
        assertThat(content).doesNotContain("@TableLogic");
        assertThat(content).contains("@TableField(\"deleted\")");
        assertThat(content).containsPattern(fieldDeclarationPattern("deleted"));
        assertThat(content).containsPattern(fieldDeclarationPattern("passwordHash"));
        assertThat(content).containsPattern(fieldDeclarationPattern("twoFactorEnabled"));
        assertThat(content).containsPattern(fieldDeclarationPattern("loginFailCount"));
        assertThat(content).containsPattern(fieldDeclarationPattern("lockUntil"));
    }

    private static Path repositorySourcePath() {
        Path repoPath = sourceRoot.resolve("com/geihou/module/system/dal/mysql/auth/AuthUserRepository.java");
        assertThat(repoPath).exists();
        return repoPath;
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
