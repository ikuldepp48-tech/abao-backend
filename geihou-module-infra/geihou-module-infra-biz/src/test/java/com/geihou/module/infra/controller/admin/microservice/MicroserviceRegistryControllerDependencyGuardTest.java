package com.geihou.module.infra.controller.admin.microservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MicroserviceRegistryControllerDependencyGuardTest {

    private static String controllerSource;
    private static String pageReqVoSource;
    private static String respVoSource;
    private static String pomSource;

    @BeforeAll
    static void loadSources() throws IOException {
        Path moduleRoot = resolveModuleRoot(Paths.get("").toAbsolutePath());
        Path mainRoot = moduleRoot.resolve("src/main/java");
        controllerSource = Files.readString(mainRoot.resolve(
                "com/geihou/module/infra/controller/admin/microservice/MicroserviceRegistryController.java"));
        pageReqVoSource = Files.readString(mainRoot.resolve(
                "com/geihou/module/infra/controller/admin/microservice/vo/MicroserviceRegistryPageReqVO.java"));
        respVoSource = Files.readString(mainRoot.resolve(
                "com/geihou/module/infra/controller/admin/microservice/vo/MicroserviceRegistryRespVO.java"));
        pomSource = Files.readString(moduleRoot.resolve("pom.xml"));
    }

    @Test
    void controllerMustExposeOnlyAcceptedRootGetPath() {
        assertThat(controllerSource)
                .contains("@RequestMapping(\"/admin-api/infra/microservices\")")
                .contains("@GetMapping");
        for (String forbidden : List.of(
                "\"/page\"",
                "\"/health\"",
                "@PostMapping",
                "@PutMapping",
                "@DeleteMapping",
                "@PatchMapping")) {
            assertThat(controllerSource).doesNotContain(forbidden);
        }
    }

    @Test
    void responseVoMustNotExposeRejectedFields() {
        for (String forbidden : List.of(
                "creator",
                "updater",
                "deleted",
                "status",
                "host",
                "contextPath",
                "description",
                "subsystemName")) {
            assertThat(respVoSource).doesNotContain(forbidden);
        }
    }

    @Test
    void controllerAndVosMustNotUseUnapprovedFrameworks() {
        String combined = controllerSource + "\n" + pageReqVoSource + "\n" + respVoSource;
        for (String forbidden : List.of(
                "@Valid",
                "jakarta.validation",
                "javax.validation",
                "springdoc",
                "knife4j",
                "swagger",
                "@PreAuthorize",
                "Security",
                "OperateLog",
                "TenantContext",
                "ServiceHealthLog",
                "Flyway",
                "db/migration")) {
            assertThat(combined).doesNotContain(forbidden);
        }
    }

    @Test
    void pomMustOnlyAddApprovedWebStarter() {
        assertThat(pomSource).contains("spring-boot-starter-web");
        for (String forbidden : List.of(
                "spring-boot-starter-validation",
                "spring-boot-starter-security",
                "springdoc",
                "knife4j",
                "swagger")) {
            assertThat(pomSource).doesNotContain(forbidden);
        }
    }

    private static Path resolveModuleRoot(Path cwd) {
        for (Path current = cwd; current != null; current = current.getParent()) {
            for (String relativePath : List.of(
                    "",
                    "geihou-module-infra/geihou-module-infra-biz",
                    "abao-backend/geihou-module-infra/geihou-module-infra-biz")) {
                Path candidate = current.resolve(relativePath).normalize();
                if (Files.isRegularFile(candidate.resolve("pom.xml"))
                        && Files.isDirectory(candidate.resolve(
                                "src/main/java/com/geihou/module/infra/service/microservice"))) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("Cannot resolve infra-biz module root from cwd=" + cwd);
    }
}
