package com.geihou.module.system.dal.mysql.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.module.system.dal.dataobject.auth.AuthUserDO;
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
        classes = AuthUserRepositorySmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_system_auth_user_repo_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class AuthUserRepositorySmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({GeihouMyBatisAutoConfiguration.class, AuthUserRepository.class})
    @MapperScan("com.geihou.module.system.dal.mysql.auth")
    static class TestConfig {
    }

    @Autowired
    private AuthUserMapper mapper;

    @Autowired
    private AuthUserRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_user");
        AuthUserMapperDbSmokeTest.createAuthUserTable(jdbcTemplate);
        mapper.insert(AuthUserMapperDbSmokeTest.newUser(
                1L, "repo-owner", "13800001001", "openid-repo-owner", "OWNER", "ACTIVE", false));
        mapper.insert(AuthUserMapperDbSmokeTest.newUser(
                1L, "repo-staff", "13800001002", "openid-repo-staff", "STAFF", "DISABLED", false));
        mapper.insert(AuthUserMapperDbSmokeTest.newUser(
                1L, "repo-deleted", "13800001003", "openid-repo-deleted", "CUSTOMER", "ACTIVE", true));
    }

    @Test
    void shouldInsertAndSelectById() {
        AuthUserDO user = AuthUserMapperDbSmokeTest.newUser(
                2L, "repo-inserted", "13800001004", "openid-repo-inserted", "CONSULTANT", "ACTIVE", false);

        assertThat(repository.insert(user)).isEqualTo(1);
        assertThat(user.getId()).isNotNull();

        AuthUserDO selected = repository.selectById(user.getId());
        assertThat(selected).isNotNull();
        assertThat(selected.getTenantId()).isEqualTo(2L);
        assertThat(selected.getUsername()).isEqualTo("repo-inserted");
    }

    @Test
    void shouldSelectByTenantIdentifiers() {
        assertThat(repository.selectByTenantIdAndPhone(1L, "13800001001").getUsername())
                .isEqualTo("repo-owner");
        assertThat(repository.selectByTenantIdAndUsername(1L, "repo-staff").getStatus())
                .isEqualTo("DISABLED");
        assertThat(repository.selectByTenantIdAndWechatOpenid(1L, "openid-repo-owner").getPhone())
                .isEqualTo("13800001001");
    }

    @Test
    void shouldCheckExistsByTenantIdentifiers() {
        assertThat(repository.existsByTenantIdAndPhone(1L, "13800001001")).isTrue();
        assertThat(repository.existsByTenantIdAndPhone(2L, "13800001001")).isFalse();
        assertThat(repository.existsByTenantIdAndUsername(1L, "repo-owner")).isTrue();
        assertThat(repository.existsByTenantIdAndUsername(1L, "repo-missing")).isFalse();
        assertThat(repository.existsByTenantIdAndWechatOpenid(1L, "openid-repo-owner")).isTrue();
        assertThat(repository.existsByTenantIdAndWechatOpenid(1L, "openid-repo-missing")).isFalse();
    }

    @Test
    void shouldIgnoreDeletedRows() {
        AuthUserDO deleted = mapper.selectOne(AuthUserDO::getUsername, "repo-deleted");

        assertThat(deleted).isNotNull();
        assertThat(deleted.getDeleted()).isTrue();
        assertThat(repository.selectById(deleted.getId())).isNull();
        assertThat(repository.selectByTenantIdAndPhone(1L, "13800001003")).isNull();
        assertThat(repository.selectByTenantIdAndUsername(1L, "repo-deleted")).isNull();
        assertThat(repository.selectByTenantIdAndWechatOpenid(1L, "openid-repo-deleted")).isNull();
        assertThat(repository.existsByTenantIdAndPhone(1L, "13800001003")).isFalse();
    }

    @Test
    void shouldUpdateLoginFailureStateAndResetLoginState() {
        AuthUserDO owner = repository.selectByTenantIdAndUsername(1L, "repo-owner");
        LocalDateTime now = LocalDateTime.parse("2026-06-20T02:00:00");
        LocalDateTime lockUntil = LocalDateTime.parse("2026-06-20T02:30:00");

        assertThat(repository.updateLoginFailureState(
                owner.getId(), 5, "LOCKED", "LOGIN_FAIL_LOCKED", lockUntil, now)).isEqualTo(1);

        AuthUserDO locked = repository.selectById(owner.getId());
        assertThat(locked.getStatus()).isEqualTo("LOCKED");
        assertThat(locked.getStatusReason()).isEqualTo("LOGIN_FAIL_LOCKED");
        assertThat(locked.getLoginFailCount()).isEqualTo(5);
        assertThat(locked.getLockUntil()).isEqualTo(lockUntil);

        assertThat(repository.resetLoginState(owner.getId(), "127.0.0.2", now.plusMinutes(31))).isEqualTo(1);

        AuthUserDO reset = repository.selectById(owner.getId());
        assertThat(reset.getStatus()).isEqualTo("ACTIVE");
        assertThat(reset.getStatusReason()).isNull();
        assertThat(reset.getLoginFailCount()).isZero();
        assertThat(reset.getLockUntil()).isNull();
        assertThat(reset.getLastLoginIp()).isEqualTo("127.0.0.2");
    }
}
