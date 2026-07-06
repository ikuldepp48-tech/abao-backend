package com.geihou.module.system.dal.mysql.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.module.system.dal.dataobject.auth.AuthTwoFactorTempTokenDO;
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
        classes = AuthTwoFactorTempTokenRepositorySmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_two_factor_temp_token_repo_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class AuthTwoFactorTempTokenRepositorySmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({GeihouMyBatisAutoConfiguration.class, AuthTwoFactorTempTokenRepository.class})
    @MapperScan("com.geihou.module.system.dal.mysql.auth")
    static class TestConfig {
    }

    @Autowired
    private AuthTwoFactorTempTokenRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_two_factor_temp_token");
        jdbcTemplate.execute("""
                CREATE TABLE auth_two_factor_temp_token (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    temp_token_key VARCHAR(64) NOT NULL,
                    token_hash CHAR(64) NOT NULL,
                    user_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL DEFAULT 0,
                    user_role VARCHAR(32) NOT NULL,
                    expire_time TIMESTAMP NOT NULL,
                    consumed_time TIMESTAMP,
                    two_factor_fail_count INT NOT NULL DEFAULT 0,
                    create_time TIMESTAMP NOT NULL,
                    CONSTRAINT uk_temp_token_key UNIQUE (temp_token_key)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX idx_expire_time ON auth_two_factor_temp_token (expire_time)");
        jdbcTemplate.execute("CREATE INDEX idx_user_id ON auth_two_factor_temp_token (user_id)");
        jdbcTemplate.execute("CREATE INDEX idx_consumed_time ON auth_two_factor_temp_token (consumed_time)");
    }

    @Test
    void shouldInsertSelectUsableAndConsumeOnce() {
        AuthTwoFactorTempTokenDO entity = token("temp-1", "hash-1", "2026-06-20T03:05:00");

        assertThat(repository.insert(entity)).isEqualTo(1);
        assertThat(repository.selectUsableByTokenKey("temp-1", LocalDateTime.parse("2026-06-20T03:00:00")))
                .isNotNull();
        assertThat(repository.selectUsableByTokenKey("temp-1", LocalDateTime.parse("2026-06-20T03:06:00")))
                .isNull();

        assertThat(repository.consume("temp-1", LocalDateTime.parse("2026-06-20T03:01:00"))).isTrue();
        assertThat(repository.consume("temp-1", LocalDateTime.parse("2026-06-20T03:02:00"))).isFalse();
        assertThat(repository.selectUsableByTokenKey("temp-1", LocalDateTime.parse("2026-06-20T03:03:00")))
                .isNull();
    }

    @Test
    void shouldRecordFailureAndInvalidateAtThreshold() {
        AuthTwoFactorTempTokenDO entity = token("temp-1", "hash-1", "2026-06-20T03:05:00");
        assertThat(repository.insert(entity)).isEqualTo(1);

        LocalDateTime now = LocalDateTime.parse("2026-06-20T03:01:00");
        assertThat(repository.recordFailure("temp-1", 5, now)).isTrue();
        assertThat(repository.selectByTokenKey("temp-1").getTwoFactorFailCount()).isEqualTo(1);
        assertThat(repository.selectByTokenKey("temp-1").getConsumedTime()).isNull();

        assertThat(repository.recordFailure("temp-1", 5, now.plusSeconds(1))).isTrue();
        assertThat(repository.recordFailure("temp-1", 5, now.plusSeconds(2))).isTrue();
        assertThat(repository.recordFailure("temp-1", 5, now.plusSeconds(3))).isTrue();
        LocalDateTime thresholdTime = now.plusSeconds(4);
        assertThat(repository.recordFailure("temp-1", 5, thresholdTime)).isTrue();

        AuthTwoFactorTempTokenDO afterThreshold = repository.selectByTokenKey("temp-1");
        assertThat(afterThreshold.getTwoFactorFailCount()).isEqualTo(5);
        assertThat(afterThreshold.getConsumedTime()).isEqualTo(thresholdTime);
        assertThat(repository.recordFailure("temp-1", 5, now.plusSeconds(5))).isFalse();
        assertThat(repository.selectUsableByTokenKey("temp-1", now.plusSeconds(5))).isNull();
    }

    private static AuthTwoFactorTempTokenDO token(String key, String tokenHash, String expireTime) {
        AuthTwoFactorTempTokenDO entity = new AuthTwoFactorTempTokenDO();
        entity.setTempTokenKey(key);
        entity.setTokenHash(tokenHash);
        entity.setUserId(2001L);
        entity.setTenantId(0L);
        entity.setUserRole("CONSULTANT");
        entity.setExpireTime(LocalDateTime.parse(expireTime));
        entity.setCreateTime(LocalDateTime.parse("2026-06-20T03:00:00"));
        return entity;
    }
}
