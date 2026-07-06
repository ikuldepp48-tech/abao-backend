package com.geihou.module.system.dal.mysql.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.module.system.dal.dataobject.auth.AuthLoginAttemptDO;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        classes = AuthLoginAttemptRepositorySmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_auth_login_attempt_repo_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class AuthLoginAttemptRepositorySmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({GeihouMyBatisAutoConfiguration.class, AuthLoginAttemptRepository.class})
    @MapperScan("com.geihou.module.system.dal.mysql.auth")
    static class TestConfig {
    }

    @Autowired
    private AuthLoginAttemptRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_login_attempt");
        jdbcTemplate.execute("""
                CREATE TABLE auth_login_attempt (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(64),
                    auth_status VARCHAR(32) NOT NULL,
                    deny_reason VARCHAR(64),
                    client_ip VARCHAR(64),
                    user_agent VARCHAR(512),
                    attempt_time TIMESTAMP NOT NULL,
                    create_time TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("CREATE INDEX idx_username_time ON auth_login_attempt (username, attempt_time)");
        jdbcTemplate.execute("CREATE INDEX idx_status_time ON auth_login_attempt (auth_status, attempt_time)");
    }

    @Test
    void shouldInsertAndSelectRecentByUsername() {
        assertThat(repository.insert(attempt("owner", "DENIED", "INVALID_PASSWORD", "2026-06-19T09:00:00")))
                .isEqualTo(1);
        assertThat(repository.insert(attempt("owner", "AUTHENTICATED", null, "2026-06-19T09:05:00")))
                .isEqualTo(1);
        assertThat(repository.insert(attempt("other", "DENIED", "UNKNOWN_USER", "2026-06-19T09:10:00")))
                .isEqualTo(1);

        assertThat(repository.selectRecentByUsername("owner", 1))
                .singleElement()
                .satisfies(attempt -> {
                    assertThat(attempt.getAuthStatus()).isEqualTo("AUTHENTICATED");
                    assertThat(attempt.getDenyReason()).isNull();
                });
    }

    private static AuthLoginAttemptDO attempt(String username, String status, String denyReason, String time) {
        AuthLoginAttemptDO attempt = new AuthLoginAttemptDO();
        attempt.setUsername(username);
        attempt.setAuthStatus(status);
        attempt.setDenyReason(denyReason);
        attempt.setClientIp("127.0.0.1");
        attempt.setUserAgent("JUnit");
        attempt.setAttemptTime(LocalDateTime.parse(time));
        attempt.setCreateTime(LocalDateTime.parse(time));
        return attempt;
    }
}
