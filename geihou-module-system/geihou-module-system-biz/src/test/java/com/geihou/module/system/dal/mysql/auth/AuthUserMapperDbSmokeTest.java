package com.geihou.module.system.dal.mysql.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        classes = AuthUserMapperDbSmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_system_auth_user_mapper_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class AuthUserMapperDbSmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import(GeihouMyBatisAutoConfiguration.class)
    @MapperScan("com.geihou.module.system.dal.mysql.auth")
    static class TestConfig {
    }

    @Autowired
    private AuthUserMapper mapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MybatisPlusInterceptor mybatisPlusInterceptor;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_user");
        createAuthUserTable(jdbcTemplate);
        mapper.insert(newUser(1L, "owner-alpha", "13800000001", "openid-alpha", "OWNER", "ACTIVE", false));
        mapper.insert(newUser(1L, "staff-beta", "13800000002", "openid-beta", "STAFF", "LOCKED", false));
        mapper.insert(newUser(2L, "owner-gamma", "13800000001", "openid-gamma", "OWNER", "ACTIVE", false));
        mapper.insert(newUser(1L, "deleted-delta", "13800000004", "openid-delta", "CUSTOMER", "ACTIVE", true));
    }

    @Test
    void shouldRegisterPaginationInterceptor() {
        assertThat(mybatisPlusInterceptor.getInterceptors())
                .singleElement()
                .isInstanceOfSatisfying(PaginationInnerInterceptor.class, pagination ->
                        assertThat(pagination.getDbType()).isEqualTo(DbType.MYSQL));
    }

    @Test
    void shouldInsertAndSelectById() {
        AuthUserDO user = newUser(1L, "consultant-inserted", "13800000003",
                "openid-inserted", "CONSULTANT", "ACTIVE", false);

        assertThat(mapper.insert(user)).isEqualTo(1);
        assertThat(user.getId()).isNotNull();

        AuthUserDO selected = mapper.selectById(user.getId());
        assertThat(selected.getUsername()).isEqualTo("consultant-inserted");
        assertThat(selected.getTenantId()).isEqualTo(1L);
        assertThat(selected.getUserRole()).isEqualTo("CONSULTANT");
    }

    @Test
    void shouldSelectByTenantAndIdentifiers() {
        AuthUserDO byPhone = mapper.selectOne(new LambdaQueryWrapper<AuthUserDO>()
                .eq(AuthUserDO::getTenantId, 1L)
                .eq(AuthUserDO::getPhone, "13800000001")
                .eq(AuthUserDO::getDeleted, false));
        AuthUserDO byUsername = mapper.selectOne(new LambdaQueryWrapper<AuthUserDO>()
                .eq(AuthUserDO::getTenantId, 1L)
                .eq(AuthUserDO::getUsername, "staff-beta")
                .eq(AuthUserDO::getDeleted, false));
        AuthUserDO byOpenid = mapper.selectOne(new LambdaQueryWrapper<AuthUserDO>()
                .eq(AuthUserDO::getTenantId, 2L)
                .eq(AuthUserDO::getWechatOpenid, "openid-gamma")
                .eq(AuthUserDO::getDeleted, false));

        assertThat(byPhone.getUsername()).isEqualTo("owner-alpha");
        assertThat(byUsername.getStatus()).isEqualTo("LOCKED");
        assertThat(byOpenid.getUsername()).isEqualTo("owner-gamma");
    }

    @Test
    void shouldRejectDuplicateActiveIdentifiersInSameTenant() {
        AuthUserDO duplicatePhone = newUser(1L, "another-owner", "13800000001",
                "openid-another", "OWNER", "ACTIVE", false);

        assertThatThrownBy(() -> mapper.insert(duplicatePhone))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAllowSamePhoneAcrossDifferentTenant() {
        assertThat(mapper.selectList(new LambdaQueryWrapper<AuthUserDO>()
                .eq(AuthUserDO::getPhone, "13800000001")
                .eq(AuthUserDO::getDeleted, false)))
                .extracting(AuthUserDO::getTenantId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void shouldKeepDeletedFlagAsPlainMappedField() {
        AuthUserDO deleted = mapper.selectOne(new LambdaQueryWrapper<AuthUserDO>()
                .eq(AuthUserDO::getTenantId, 1L)
                .eq(AuthUserDO::getUsername, "deleted-delta")
                .eq(AuthUserDO::getDeleted, true));

        assertThat(deleted).isNotNull();
        assertThat(deleted.getDeleted()).isTrue();
    }

    static void createAuthUserTable(JdbcTemplate jdbcTemplate) {
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
                    deleted BOOLEAN NOT NULL DEFAULT FALSE,
                    CONSTRAINT uk_tenant_phone UNIQUE (tenant_id, phone, deleted),
                    CONSTRAINT uk_tenant_username UNIQUE (tenant_id, username, deleted),
                    CONSTRAINT uk_tenant_openid UNIQUE (tenant_id, wechat_openid, deleted)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX idx_tenant_role ON auth_user (tenant_id, user_role, status)");
        jdbcTemplate.execute("CREATE INDEX idx_phone ON auth_user (phone)");
        jdbcTemplate.execute("CREATE INDEX idx_email ON auth_user (email)");
    }

    static AuthUserDO newUser(Long tenantId, String username, String phone, String wechatOpenid,
                              String userRole, String status, boolean deleted) {
        AuthUserDO user = new AuthUserDO();
        user.setTenantId(tenantId);
        user.setUserRole(userRole);
        user.setUsername(username);
        user.setPhone(phone);
        user.setEmail(username + "@example.test");
        user.setWechatOpenid(wechatOpenid);
        user.setWechatUnionid("union-" + username);
        user.setPasswordHash(null);
        user.setPasswordSalt(null);
        user.setTwoFactorEnabled("CONSULTANT".equals(userRole));
        user.setTwoFactorSecret(null);
        user.setNickname(username);
        user.setAvatar("https://example.test/avatar/" + username);
        user.setStatus(status);
        user.setStatusReason("LOCKED".equals(status) ? "test locked" : null);
        user.setLastLoginTime(LocalDateTime.parse("2026-06-12T08:00:00"));
        user.setLastLoginIp("127.0.0.1");
        user.setLoginFailCount("LOCKED".equals(status) ? 5 : 0);
        if ("LOCKED".equals(status)) {
            user.setLockUntil(LocalDateTime.parse("2026-06-12T08:30:00"));
        }
        user.setCreator("h70");
        user.setCreateTime(LocalDateTime.parse("2026-06-12T08:00:00"));
        user.setUpdater("h70");
        user.setUpdateTime(LocalDateTime.parse("2026-06-12T08:00:00"));
        user.setDeleted(deleted);
        return user;
    }
}
