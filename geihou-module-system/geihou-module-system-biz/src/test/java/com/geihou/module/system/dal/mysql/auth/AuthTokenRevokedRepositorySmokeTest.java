package com.geihou.module.system.dal.mysql.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.module.system.dal.dataobject.auth.AuthTokenRevokedDO;
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
        classes = AuthTokenRevokedRepositorySmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_system_auth_token_revoked_repo_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class AuthTokenRevokedRepositorySmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({GeihouMyBatisAutoConfiguration.class, AuthTokenRevokedRepository.class})
    @MapperScan("com.geihou.module.system.dal.mysql.auth")
    static class TestConfig {
    }

    @Autowired
    private AuthTokenRevokedRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_token_revoked");
        createAuthTokenRevokedTable(jdbcTemplate);
        insertSeed(jdbcTemplate, "repo-expired", 100L, "LOGOUT",
                LocalDateTime.parse("2026-06-12T08:00:00"),
                LocalDateTime.parse("2026-06-12T09:00:00"));
        insertSeed(jdbcTemplate, "repo-active-1", 100L, "PASSWORD_CHANGE",
                LocalDateTime.parse("2026-06-12T08:10:00"),
                LocalDateTime.parse("2026-06-14T09:00:00"));
        insertSeed(jdbcTemplate, "repo-active-2", 200L, "FORCE_OFFLINE",
                LocalDateTime.parse("2026-06-12T08:20:00"),
                LocalDateTime.parse("2026-06-15T09:00:00"));
    }

    @Test
    void shouldInsertAndSelectByJti() {
        AuthTokenRevokedDO token = newToken("repo-inserted", 300L,
                LocalDateTime.parse("2026-06-16T09:00:00"));

        assertThat(repository.insert(token)).isEqualTo(1);
        assertThat(token.getId()).isNotNull();

        AuthTokenRevokedDO selected = repository.selectByJti("repo-inserted");
        assertThat(selected).isNotNull();
        assertThat(selected.getUserId()).isEqualTo(300L);
        assertThat(selected.getRevokeReason()).isEqualTo("ROLE_CHANGE");
    }

    @Test
    void shouldSelectByUserId() {
        assertThat(repository.selectByUserId(100L))
                .extracting(AuthTokenRevokedDO::getJti)
                .containsExactlyInAnyOrder("repo-expired", "repo-active-1");
    }

    @Test
    void shouldCheckExistsByJti() {
        assertThat(repository.existsByJti("repo-active-1")).isTrue();
        assertThat(repository.existsByJti("repo-missing")).isFalse();
    }

    @Test
    void shouldDeleteExpired() {
        int deleted = repository.deleteExpired(LocalDateTime.parse("2026-06-13T00:00:00"));

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.selectByJti("repo-expired")).isNull();
        assertThat(repository.selectByJti("repo-active-1")).isNotNull();
        assertThat(repository.selectByJti("repo-active-2")).isNotNull();
    }

    private static void createAuthTokenRevokedTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE auth_token_revoked (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    jti VARCHAR(64) NOT NULL,
                    user_id BIGINT NOT NULL,
                    revoke_reason VARCHAR(64) NOT NULL,
                    revoke_time TIMESTAMP NOT NULL,
                    expire_time TIMESTAMP NOT NULL,
                    create_time TIMESTAMP NOT NULL,
                    CONSTRAINT uk_jti UNIQUE (jti)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX idx_user ON auth_token_revoked (user_id, revoke_time)");
        jdbcTemplate.execute("CREATE INDEX idx_expire_time ON auth_token_revoked (expire_time)");
    }

    private static void insertSeed(JdbcTemplate jdbcTemplate, String jti, Long userId, String revokeReason,
                                   LocalDateTime revokeTime, LocalDateTime expireTime) {
        jdbcTemplate.update("""
                INSERT INTO auth_token_revoked (jti, user_id, revoke_reason, revoke_time, expire_time, create_time)
                VALUES (?, ?, ?, ?, ?, ?)
                """, jti, userId, revokeReason, revokeTime, expireTime, revokeTime);
    }

    private static AuthTokenRevokedDO newToken(String jti, Long userId, LocalDateTime expireTime) {
        AuthTokenRevokedDO token = new AuthTokenRevokedDO();
        token.setJti(jti);
        token.setUserId(userId);
        token.setRevokeReason("ROLE_CHANGE");
        token.setRevokeTime(LocalDateTime.parse("2026-06-12T08:30:00"));
        token.setExpireTime(expireTime);
        token.setCreateTime(LocalDateTime.parse("2026-06-12T08:30:01"));
        return token;
    }
}
