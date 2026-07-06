package com.geihou.module.infra.service.microservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MicroserviceRegistryServiceDependencyGuardTest {

    private static String serviceSource;

    @BeforeAll
    static void loadServiceSource() throws IOException {
        Path servicePath = resolveSourceRoot(Paths.get("").toAbsolutePath())
                .resolve("com/geihou/module/infra/service/microservice/MicroserviceRegistryService.java");
        assertThat(servicePath).exists();
        serviceSource = Files.readString(servicePath);
    }

    @Test
    void serviceMustNotReferenceMapper() {
        assertThat(serviceSource).doesNotContain("MicroserviceRegistryMapper");
    }

    @Test
    void serviceMustNotContainForbiddenMutatorKeywords() {
        for (String keyword : List.of("insert", "update", "delete", "save", "upsert", "batchInsert", "batchUpdate")) {
            assertThat(serviceSource).doesNotContain(keyword);
        }
    }

    @Test
    void serviceMustNotContainForbiddenRuntimeTokens() {
        List<String> forbiddenTokens = List.of(
                "@RestController",
                "@Controller",
                "@RequestMapping",
                "@Scheduled",
                "@Async",
                "@PreAuthorize",
                "@TenantIgnore",
                "@OperateLog",
                "RestTemplate",
                "WebClient",
                "HttpClient",
                "Kafka",
                "Rabbit",
                "RocketMQ");
        for (String token : forbiddenTokens) {
            assertThat(serviceSource).doesNotContain(token);
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
