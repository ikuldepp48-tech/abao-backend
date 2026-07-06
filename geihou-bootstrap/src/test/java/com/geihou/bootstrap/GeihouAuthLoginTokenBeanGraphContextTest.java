package com.geihou.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.module.system.dal.mysql.auth.AuthPermissionRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRolePermissionRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRoleRepository;
import com.geihou.module.system.dal.mysql.auth.AuthTokenRevokedRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRoleRepository;
import com.geihou.module.system.framework.jwt.GeihouActiveKidProvider;
import com.geihou.module.system.framework.jwt.GeihouSigningSecret;
import com.geihou.module.system.framework.jwt.GeihouSigningSecretProvider;
import com.geihou.module.system.service.auth.GeihouAccessTokenIssueService;
import com.geihou.module.system.service.auth.GeihouAuthLoginTokenRefreshService;
import com.geihou.module.system.service.auth.GeihouAuthLoginTokenWiringService;
import com.geihou.module.system.service.auth.GeihouAuthRolePermissionReadService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        classes = GeihouAuthLoginTokenBeanGraphContextTest.TestConfig.class,
        properties = {
                "spring.flyway.locations=classpath:db/migration",
                "spring.flyway.clean-disabled=true",
                "spring.flyway.baseline-on-migrate=false",
                "spring.main.web-application-type=none"
        }
)
class GeihouAuthLoginTokenBeanGraphContextTest {

    private static final GeihouSigningSecret SIGNING_SECRET =
            new GeihouSigningSecret("kid-h156-context", "HS512", secretBytes());

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("geihou_h156")
            .withUsername("geihou")
            .withPassword("geihou");

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Test
    void shouldLoadLoginTokenServiceBeanGraphFromAutoConfiguration() {
        assertThat(context.getBeansOfType(GeihouAccessTokenIssueService.class)).hasSize(1);
        assertThat(context.getBeansOfType(GeihouAuthRolePermissionReadService.class)).hasSize(1);
        assertThat(context.getBeansOfType(GeihouAuthLoginTokenWiringService.class)).hasSize(1);
        assertThat(context.getBeansOfType(GeihouAuthLoginTokenRefreshService.class)).hasSize(1);
    }

    @Configuration
    @EnableAutoConfiguration
    @MapperScan("com.geihou.module.system.dal.mysql.auth")
    @Import({
            AuthUserRoleRepository.class,
            AuthRoleRepository.class,
            AuthRolePermissionRepository.class,
            AuthPermissionRepository.class,
            AuthTokenRevokedRepository.class,
            AuthUserRepository.class
    })
    static class TestConfig {

        @Bean
        GeihouActiveKidProvider activeKidProvider() {
            return () -> Optional.of("kid-h156-context");
        }

        @Bean
        GeihouSigningSecretProvider signingSecretProvider() {
            return kid -> "kid-h156-context".equals(kid) ? Optional.of(SIGNING_SECRET) : Optional.empty();
        }
    }

    private static byte[] secretBytes() {
        byte[] bytes = new byte[64];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i + 1);
        }
        return bytes;
    }
}
