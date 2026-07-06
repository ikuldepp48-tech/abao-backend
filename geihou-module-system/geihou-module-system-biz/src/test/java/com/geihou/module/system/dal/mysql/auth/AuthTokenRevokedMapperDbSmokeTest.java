package com.geihou.module.system.dal.mysql.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        classes = AuthTokenRevokedMapperDbSmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_system_auth_token_revoked_mapper_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class AuthTokenRevokedMapperDbSmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import(GeihouMyBatisAutoConfiguration.class)
    @MapperScan("com.geihou.module.system.dal.mysql.auth")
    static class TestConfig {
    }

    @Autowired
    private AuthTokenRevokedMapper mapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MybatisPlusInterceptor mybatisPlusInterceptor;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_token_revoked");
        createAuthTokenRevokedTable(jdbcTemplate);
        insertSeed(jdbcTemplate, "jti-expired", 100L, "LOGOUT",
                LocalDateTime.parse("2026-06-12T08:00:00"),
                LocalDateTime.parse("2026-06-12T09:00:00"));
        insertSeed(jdbcTemplate, "jti-active-1", 100L, "PASSWORD_CHANGE",
                LocalDateTime.parse("2026-06-12T08:10:00"),
                LocalDateTime.parse("2026-06-14T09:00:00"));
        insertSeed(jdbcTemplate, "jti-active-2", 200L, "FORCE_OFFLINE",
                LocalDateTime.parse("2026-06-12T08:20:00"),
                LocalDateTime.parse("2026-06-15T09:00:00"));
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
        AuthTokenRevokedDO token = newToken("jti-inserted", 300L,
                LocalDateTime.parse("2026-06-16T09:00:00"));

        assertThat(mapper.insert(token)).isEqualTo(1);
        assertThat(token.getId()).isNotNull();

        AuthTokenRevokedDO selected = mapper.selectById(token.getId());
        assertThat(selected.getJti()).isEqualTo("jti-inserted");
        assertThat(selected.getUserId()).isEqualTo(300L);
        assertThat(selected.getRevokeReason()).isEqualTo("ROLE_CHANGE");
    }

    @Test
    void shouldSelectByJti() {
        AuthTokenRevokedDO token = mapper.selectOne(AuthTokenRevokedDO::getJti, "jti-active-1");

        assertThat(token).isNotNull();
        assertThat(token.getUserId()).isEqualTo(100L);
        assertThat(token.getRevokeReason()).isEqualTo("PASSWORD_CHANGE");
    }

    @Test
    void shouldSelectListByUserId() {
        assertThat(mapper.selectList(AuthTokenRevokedDO::getUserId, 100L))
                .extracting(AuthTokenRevokedDO::getJti)
                .containsExactlyInAnyOrder("jti-expired", "jti-active-1");
    }

    @Test
    void shouldCountByJti() {
        assertThat(mapper.selectCount(AuthTokenRevokedDO::getJti, "jti-active-1")).isEqualTo(1L);
        assertThat(mapper.selectCount(AuthTokenRevokedDO::getJti, "missing-jti")).isZero();
    }

    @Test
    void shouldRejectDuplicateJti() {
        AuthTokenRevokedDO duplicate = newToken("jti-active-1", 900L,
                LocalDateTime.parse("2026-06-16T09:00:00"));

        assertThatThrownBy(() -> mapper.insert(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldDeleteExpiredAndKeepNonExpiredRows() {
        int deleted = mapper.delete(new LambdaQueryWrapper<AuthTokenRevokedDO>()
                .lt(AuthTokenRevokedDO::getExpireTime, LocalDateTime.parse("2026-06-13T00:00:00")));

        assertThat(deleted).isEqualTo(1);
        assertThat(mapper.selectOne(AuthTokenRevokedDO::getJti, "jti-expired")).isNull();
        assertThat(mapper.selectOne(AuthTokenRevokedDO::getJti, "jti-active-1")).isNotNull();
        assertThat(mapper.selectOne(AuthTokenRevokedDO::getJti, "jti-active-2")).isNotNull();
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
