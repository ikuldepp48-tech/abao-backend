package com.geihou.module.system.dal.mysql.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.module.system.dal.dataobject.auth.AuthCaptchaChallengeDO;
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
        classes = AuthCaptchaChallengeRepositorySmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_auth_captcha_repo_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class AuthCaptchaChallengeRepositorySmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({GeihouMyBatisAutoConfiguration.class, AuthCaptchaChallengeRepository.class})
    @MapperScan("com.geihou.module.system.dal.mysql.auth")
    static class TestConfig {
    }

    @Autowired
    private AuthCaptchaChallengeRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_captcha_challenge");
        jdbcTemplate.execute("""
                CREATE TABLE auth_captcha_challenge (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    captcha_key VARCHAR(64) NOT NULL,
                    answer_hash CHAR(64) NOT NULL,
                    expire_time TIMESTAMP NOT NULL,
                    consumed_time TIMESTAMP,
                    create_time TIMESTAMP NOT NULL,
                    CONSTRAINT uk_captcha_key UNIQUE (captcha_key)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX idx_expire_time ON auth_captcha_challenge (expire_time)");
        jdbcTemplate.execute("CREATE INDEX idx_consumed_time ON auth_captcha_challenge (consumed_time)");
    }

    @Test
    void shouldInsertSelectUsableAndConsumeOnce() {
        AuthCaptchaChallengeDO entity = challenge("captcha-1", "hash-1", "2026-06-20T01:05:00");

        assertThat(repository.insert(entity)).isEqualTo(1);
        assertThat(repository.selectUsableByKey("captcha-1", LocalDateTime.parse("2026-06-20T01:00:00")))
                .isNotNull();
        assertThat(repository.selectUsableByKey("captcha-1", LocalDateTime.parse("2026-06-20T01:06:00")))
                .isNull();

        assertThat(repository.consume("captcha-1", LocalDateTime.parse("2026-06-20T01:01:00"))).isTrue();
        assertThat(repository.consume("captcha-1", LocalDateTime.parse("2026-06-20T01:02:00"))).isFalse();
        assertThat(repository.selectUsableByKey("captcha-1", LocalDateTime.parse("2026-06-20T01:03:00")))
                .isNull();
    }

    private static AuthCaptchaChallengeDO challenge(String key, String answerHash, String expireTime) {
        AuthCaptchaChallengeDO entity = new AuthCaptchaChallengeDO();
        entity.setCaptchaKey(key);
        entity.setAnswerHash(answerHash);
        entity.setExpireTime(LocalDateTime.parse(expireTime));
        entity.setCreateTime(LocalDateTime.parse("2026-06-20T01:00:00"));
        return entity;
    }
}
