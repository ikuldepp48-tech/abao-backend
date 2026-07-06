package com.geihou.module.system.dal.mysql.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.module.system.dal.dataobject.auth.AuthRefreshTokenDO;
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
        classes = AuthRefreshTokenRepositorySmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_refresh_token_repo_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class AuthRefreshTokenRepositorySmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({GeihouMyBatisAutoConfiguration.class, AuthRefreshTokenRepository.class})
    @MapperScan("com.geihou.module.system.dal.mysql.auth")
    static class TestConfig {
    }

    @Autowired
    private AuthRefreshTokenRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_refresh_token");
        jdbcTemplate.execute("""
                CREATE TABLE auth_refresh_token (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    token_key VARCHAR(64) NOT NULL,
                    token_hash CHAR(64) NOT NULL,
                    user_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL DEFAULT 0,
                    user_role VARCHAR(32) NOT NULL,
                    expire_time TIMESTAMP NOT NULL,
                    consumed_time TIMESTAMP,
                    revoked_time TIMESTAMP,
                    revoke_reason VARCHAR(64),
                    create_time TIMESTAMP NOT NULL,
                    CONSTRAINT uk_token_key UNIQUE (token_key)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX idx_expire_time ON auth_refresh_token (expire_time)");
        jdbcTemplate.execute("CREATE INDEX idx_user_id ON auth_refresh_token (user_id)");
        jdbcTemplate.execute("CREATE INDEX idx_consumed_time ON auth_refresh_token (consumed_time)");
        jdbcTemplate.execute("CREATE INDEX idx_revoked_time ON auth_refresh_token (revoked_time)");
    }

    @Test
    void shouldInsertSelectUsableAndKeepRefreshTokenReusable() {
        AuthRefreshTokenDO entity = token("key-1", "hash-1", "2026-06-27T03:00:00");

        assertThat(repository.insert(entity)).isEqualTo(1);
        assertThat(repository.selectUsableByTokenKey("key-1", LocalDateTime.parse("2026-06-20T03:00:00")))
                .isNotNull();
        assertThat(repository.selectUsableByTokenKey("key-1", LocalDateTime.parse("2026-06-28T03:00:00")))
                .isNull();

        assertThat(repository.selectUsableByTokenKey("key-1", LocalDateTime.parse("2026-06-20T03:01:00")))
                .isNotNull();
        assertThat(repository.selectUsableByTokenKey("key-1", LocalDateTime.parse("2026-06-20T03:02:00")))
                .isNotNull();
    }

    @Test
    void shouldRejectRevokedToken() {
        AuthRefreshTokenDO entity = token("key-2", "hash-2", "2026-06-27T03:00:00");
        entity.setRevokedTime(LocalDateTime.parse("2026-06-20T03:00:00"));

        assertThat(repository.insert(entity)).isEqualTo(1);
        assertThat(repository.selectUsableByTokenKey("key-2", LocalDateTime.parse("2026-06-20T03:01:00")))
                .isNull();
    }

    @Test
    void shouldRejectConsumedTokenForFutureRotationCompatibility() {
        AuthRefreshTokenDO entity = token("key-3", "hash-3", "2026-06-27T03:00:00");
        entity.setConsumedTime(LocalDateTime.parse("2026-06-20T03:00:00"));

        assertThat(repository.insert(entity)).isEqualTo(1);
        assertThat(repository.selectUsableByTokenKey("key-3", LocalDateTime.parse("2026-06-20T03:01:00")))
                .isNull();
    }

    @Test
    void shouldRevokeUsableRefreshToken() {
        AuthRefreshTokenDO entity = token("key-4", "hash-4", "2026-06-27T03:00:00");

        assertThat(repository.insert(entity)).isEqualTo(1);
        assertThat(repository.revoke(
                "key-4", "ACCESS_TOKEN_ISSUE_FAILED", LocalDateTime.parse("2026-06-20T03:01:00")))
                .isTrue();
        assertThat(repository.revoke(
                "key-4", "ACCESS_TOKEN_ISSUE_FAILED", LocalDateTime.parse("2026-06-20T03:02:00")))
                .isFalse();
        assertThat(repository.selectUsableByTokenKey("key-4", LocalDateTime.parse("2026-06-20T03:03:00")))
                .isNull();
    }

    private static AuthRefreshTokenDO token(String key, String tokenHash, String expireTime) {
        AuthRefreshTokenDO entity = new AuthRefreshTokenDO();
        entity.setTokenKey(key);
        entity.setTokenHash(tokenHash);
        entity.setUserId(2001L);
        entity.setTenantId(0L);
        entity.setUserRole("CONSULTANT");
        entity.setExpireTime(LocalDateTime.parse(expireTime));
        entity.setCreateTime(LocalDateTime.parse("2026-06-20T03:00:00"));
        return entity;
    }
}
