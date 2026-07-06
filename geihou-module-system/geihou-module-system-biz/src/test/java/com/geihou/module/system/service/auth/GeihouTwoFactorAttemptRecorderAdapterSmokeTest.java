package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.module.system.dal.dataobject.auth.AuthUserDO;
import com.geihou.module.system.dal.mysql.auth.AuthLoginAttemptRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.DenyReason;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.TwoFactorAttempt;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.TwoFactorAttemptStatus;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        classes = GeihouTwoFactorAttemptRecorderAdapterSmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_2fa_attempt_recorder_smoke;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class GeihouTwoFactorAttemptRecorderAdapterSmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({
            GeihouMyBatisAutoConfiguration.class,
            AuthLoginAttemptRepository.class,
            AuthUserRepository.class
    })
    @MapperScan("com.geihou.module.system.dal.mysql.auth")
    static class TestConfig {
        @Bean
        GeihouTwoFactorAttemptRecorderAdapter geihouTwoFactorAttemptRecorderAdapter(
                AuthLoginAttemptRepository repository) {
            return new GeihouTwoFactorAttemptRecorderAdapter(repository);
        }
    }

    @Autowired
    private GeihouTwoFactorAttemptRecorderAdapter adapter;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;
    private Long userId;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_login_attempt");
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_user");
        createAuthLoginAttemptTable();
        createAuthUserTable();

        AuthUserDO user = newUser();
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret("encrypted-secret");
        user.setLoginFailCount(0);
        user.setLockUntil(null);
        authUserRepository.insert(user);
        userId = user.getId();
    }

    @Test
    void shouldPersistDeniedTwoFactorAttemptWithRealRepository() {
        adapter.record(new TwoFactorAttempt(
                "2fa-consultant",
                TwoFactorAttemptStatus.DENIED,
                DenyReason.TOTP_MISMATCH,
                "1.2.3.4",
                "Geihou-Smoke-Agent"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT username FROM auth_login_attempt WHERE deny_reason = 'TOTP_MISMATCH'",
                String.class)).isEqualTo("2fa-consultant");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT auth_status FROM auth_login_attempt WHERE deny_reason = 'TOTP_MISMATCH'",
                String.class)).isEqualTo("DENIED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT client_ip FROM auth_login_attempt WHERE deny_reason = 'TOTP_MISMATCH'",
                String.class)).isEqualTo("1.2.3.4");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_agent FROM auth_login_attempt WHERE deny_reason = 'TOTP_MISMATCH'",
                String.class)).isEqualTo("Geihou-Smoke-Agent");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempt_time FROM auth_login_attempt WHERE deny_reason = 'TOTP_MISMATCH'",
                LocalDateTime.class)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT create_time FROM auth_login_attempt WHERE deny_reason = 'TOTP_MISMATCH'",
                LocalDateTime.class)).isNotNull();
    }

    @Test
    void shouldPersistNullUsernameForInvalidTempTokenDeniedAttempt() {
        adapter.record(new TwoFactorAttempt(
                null,
                TwoFactorAttemptStatus.DENIED,
                DenyReason.TEMP_TOKEN_INVALID,
                "1.2.3.4",
                "Geihou-Smoke-Agent"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT username FROM auth_login_attempt WHERE deny_reason = 'TEMP_TOKEN_INVALID'",
                String.class)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT auth_status FROM auth_login_attempt WHERE deny_reason = 'TEMP_TOKEN_INVALID'",
                String.class)).isEqualTo("DENIED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_login_attempt WHERE deny_reason = 'TEMP_TOKEN_INVALID'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldNotModifyAuthUserLockoutFieldsWhenRecordingDeniedAttempt() {
        AuthUserDO before = authUserRepository.selectById(userId);

        adapter.record(new TwoFactorAttempt(
                "2fa-consultant",
                TwoFactorAttemptStatus.DENIED,
                DenyReason.TEMP_TOKEN_ATTEMPTS_EXCEEDED,
                "1.2.3.4",
                "Geihou-Smoke-Agent"));
        adapter.record(new TwoFactorAttempt(
                null,
                TwoFactorAttemptStatus.DENIED,
                DenyReason.TEMP_TOKEN_INVALID,
                "1.2.3.4",
                "Geihou-Smoke-Agent"));

        AuthUserDO after = authUserRepository.selectById(userId);
        assertThat(after.getLoginFailCount()).isEqualTo(before.getLoginFailCount());
        assertThat(after.getStatus()).isEqualTo(before.getStatus());
        assertThat(after.getLockUntil()).isEqualTo(before.getLockUntil());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_login_attempt", Integer.class))
                .isEqualTo(2);
    }

    private void createAuthLoginAttemptTable() {
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

    private void createAuthUserTable() {
        jdbcTemplate.execute("""
                CREATE TABLE auth_user (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL DEFAULT 0,
                    user_role VARCHAR(32) NOT NULL,
                    username VARCHAR(64),
                    phone VARCHAR(32),
                    email VARCHAR(128),
                    wechat_openid VARCHAR(64),
                    wechat_unionid VARCHAR(64),
                    password_hash VARCHAR(128),
                    password_salt VARCHAR(64),
                    two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                    two_factor_secret VARCHAR(128),
                    nickname VARCHAR(64),
                    avatar VARCHAR(512),
                    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                    status_reason VARCHAR(255),
                    last_login_time TIMESTAMP,
                    last_login_ip VARCHAR(64),
                    login_fail_count INT NOT NULL DEFAULT 0,
                    lock_until TIMESTAMP,
                    creator VARCHAR(64) NOT NULL DEFAULT '',
                    create_time TIMESTAMP NOT NULL,
                    updater VARCHAR(64) NOT NULL DEFAULT '',
                    update_time TIMESTAMP NOT NULL,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
    }

    private static AuthUserDO newUser() {
        LocalDateTime now = LocalDateTime.parse("2026-06-20T03:00:00");
        AuthUserDO user = new AuthUserDO();
        user.setTenantId(1L);
        user.setUserRole("CONSULTANT");
        user.setUsername("2fa-consultant");
        user.setPhone("13900000001");
        user.setEmail("2fa-consultant@example.test");
        user.setWechatOpenid("openid-2fa");
        user.setWechatUnionid("union-2fa");
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret("encrypted-secret");
        user.setNickname("2fa-consultant");
        user.setStatus("ACTIVE");
        user.setLastLoginTime(now);
        user.setLastLoginIp("127.0.0.1");
        user.setLoginFailCount(0);
        user.setLockUntil(null);
        user.setCreator("h157s-j");
        user.setCreateTime(now);
        user.setUpdater("h157s-j");
        user.setUpdateTime(now);
        user.setDeleted(false);
        return user;
    }
}
