package com.geihou.module.infra.service.microservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ServiceHealthLogServiceDependencyGuardTest {

    private static String serviceSource;

    @BeforeAll
    static void loadServiceSource() throws IOException {
        Path servicePath = resolveSourceRoot(Paths.get("").toAbsolutePath())
                .resolve("com/geihou/module/infra/service/microservice/ServiceHealthLogService.java");
        assertThat(servicePath).exists();
        serviceSource = Files.readString(servicePath);
    }

    @Test
    void serviceMustNotReferenceMapper() {
        assertThat(serviceSource).doesNotContain("ServiceHealthLogMapper");
    }

    @Test
    void serviceMustNotContainForbiddenMutatorKeywords() {
        for (String keyword : List.of("update", "delete", "insertOrUpdate")) {
            assertThat(serviceSource).doesNotContain(keyword);
        }
    }

    @Test
    void serviceMustNotContainForbiddenRuntimeAnnotations() {
        List<String> forbiddenAnnotations = List.of(
                "@RestController",
                "@Controller",
                "@RequestMapping",
                "@Scheduled",
                "@Async",
                "@PreAuthorize",
                "@TenantIgnore",
                "@OperateLog");
        for (String annotation : forbiddenAnnotations) {
            assertThat(serviceSource).doesNotContain(annotation);
        }
    }

    private static Path resolveSourceRoot(Path cwd) {
        for (Path current = cwd; current != null; current = current.getParent()) {
            for (String relativePath : List.of(
                    "src/main/java",
                    "geihou-module-infra/geihou-module-infra-biz/src/main/java",
                    "abao-backend/geihou-module-infra/geihou-module-infra-biz/src/main/java")) {
                Path candidate = current.resolve(relativePath).normalize();
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("Cannot resolve src/main/java from cwd=" + cwd);
    }
}
