package com.geihou.module.infra.dal.mysql.microservice;

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

class ServiceHealthLogMapperInsertOnlyGuardTest {

    private static Path sourceRoot;

    @BeforeAll
    static void resolveSourceRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path candidate = cwd.resolve("src/main/java");
        if (Files.isDirectory(candidate)) {
            sourceRoot = candidate;
            return;
        }
        candidate = cwd.resolve("../geihou-module-infra/geihou-module-infra-biz/src/main/java").normalize();
        if (Files.isDirectory(candidate)) {
            sourceRoot = candidate;
            return;
        }
        throw new IllegalStateException("Cannot resolve src/main/java directory. cwd=" + cwd);
    }

    @Test
    void repositoryMustNotExposeMutatorMethods() {
        for (Method method : ServiceHealthLogRepository.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            String name = method.getName();
            assertThat(name)
                    .as("Repository public method '%s' must not expose a forbidden mutator", name)
                    .doesNotStartWith("update")
                    .doesNotStartWith("delete")
                    .doesNotStartWith("insertOrUpdate");
        }
    }

    @Test
    void onlyMapperAndRepositoryMayReferenceServiceHealthLogMapper() throws IOException {
        Set<String> allowedFiles = Set.of("ServiceHealthLogMapper.java", "ServiceHealthLogRepository.java");
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
                String content = Files.readString(javaFile);
                if (content.contains("ServiceHealthLogMapper")) {
                    violations.add(sourceRoot.relativize(javaFile).toString());
                }
            }
        }

        assertThat(violations)
                .as("Main-code direct ServiceHealthLogMapper references must go through the repository")
                .isEmpty();
    }

    @Test
    void repositoryMustNotCallMapperMutatorMethods() throws IOException {
        Path repoPath = sourceRoot.resolve(
                "com/geihou/module/infra/dal/mysql/microservice/ServiceHealthLogRepository.java");

        assertThat(repoPath).exists();

        String content = Files.readString(repoPath);
        assertThat(content).doesNotContain("mapper.update");
        assertThat(content).doesNotContain("mapper.delete");
        assertThat(content).doesNotContain("mapper.insertOrUpdate");
    }
}
