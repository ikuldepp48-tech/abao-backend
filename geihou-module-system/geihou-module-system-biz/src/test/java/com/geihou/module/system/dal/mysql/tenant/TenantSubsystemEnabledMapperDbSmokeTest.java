package com.geihou.module.system.dal.mysql.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.geihou.common.pojo.PageResult;
import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.module.system.dal.dataobject.tenant.TenantSubsystemEnabledDO;
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
        classes = TenantSubsystemEnabledMapperDbSmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_system_tenant_subsystem_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class TenantSubsystemEnabledMapperDbSmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import(GeihouMyBatisAutoConfiguration.class)
    @MapperScan("com.geihou.module.system.dal.mysql.tenant")
    static class TestConfig {
    }

    @Autowired
    private TenantSubsystemEnabledMapper mapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MybatisPlusInterceptor mybatisPlusInterceptor;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS tenant_subsystem_enabled");
        jdbcTemplate.execute("""
                CREATE TABLE tenant_subsystem_enabled (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    subsystem_id TINYINT NOT NULL,
                    enabled BOOLEAN NOT NULL DEFAULT FALSE,
                    enable_time TIMESTAMP,
                    disable_time TIMESTAMP,
                    creator VARCHAR(64) NOT NULL DEFAULT '',
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updater VARCHAR(64) NOT NULL DEFAULT '',
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO tenant_subsystem_enabled (tenant_id, subsystem_id, enabled, enable_time, creator, updater)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?)
                """, 1L, 1, true, "h5", "h5");
        jdbcTemplate.update("""
                INSERT INTO tenant_subsystem_enabled (tenant_id, subsystem_id, enabled, enable_time, creator, updater)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?)
                """, 1L, 2, true, "h5", "h5");
        jdbcTemplate.update("""
                INSERT INTO tenant_subsystem_enabled (tenant_id, subsystem_id, enabled, enable_time, creator, updater)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?)
                """, 2L, 1, false, "h5", "h5");
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
        TenantSubsystemEnabledDO enabled = newEnabled(3L, 3, true);

        assertThat(mapper.insert(enabled)).isEqualTo(1);
        assertThat(enabled.getId()).isNotNull();

        TenantSubsystemEnabledDO selected = mapper.selectById(enabled.getId());
        assertThat(selected.getTenantId()).isEqualTo(3L);
        assertThat(selected.getSubsystemId()).isEqualTo(3);
        assertThat(selected.getEnabled()).isTrue();
    }

    @Test
    void shouldSelectOneByTwoFields() {
        TenantSubsystemEnabledDO enabled = mapper.selectOne(TenantSubsystemEnabledDO::getTenantId, 1L,
                TenantSubsystemEnabledDO::getSubsystemId, 1);

        assertThat(enabled).isNotNull();
        assertThat(enabled.getEnabled()).isTrue();
    }

    @Test
    void shouldSelectListsAndCounts() {
        assertThat(mapper.selectList()).hasSize(3);
        assertThat(mapper.selectList(TenantSubsystemEnabledDO::getTenantId, 1L)).hasSize(2);
        assertThat(mapper.selectList(TenantSubsystemEnabledDO::getTenantId, 1L,
                TenantSubsystemEnabledDO::getEnabled, true)).hasSize(2);
        assertThat(mapper.selectCount(TenantSubsystemEnabledDO::getEnabled, false)).isEqualTo(1L);
    }

    @Test
    void shouldSelectPages() {
        PageResult<TenantSubsystemEnabledDO> firstPage = mapper.selectPage(1, 2);
        assertThat(firstPage.getList()).hasSize(2);
        assertThat(firstPage.getTotal()).isEqualTo(3L);

        PageResult<TenantSubsystemEnabledDO> orderedPage = mapper.selectPage(1, 2,
                new LambdaQueryWrapper<TenantSubsystemEnabledDO>()
                        .orderByAsc(TenantSubsystemEnabledDO::getTenantId)
                        .orderByAsc(TenantSubsystemEnabledDO::getSubsystemId));
        assertThat(orderedPage.getList()).extracting(TenantSubsystemEnabledDO::getTenantId).containsExactly(1L, 1L);

        PageResult<TenantSubsystemEnabledDO> tenantPage =
                mapper.selectPage(TenantSubsystemEnabledDO::getTenantId, 1L, 1, 10);
        assertThat(tenantPage.getList()).hasSize(2);
        assertThat(tenantPage.getTotal()).isEqualTo(2L);

        PageResult<TenantSubsystemEnabledDO> tenantEnabledPage = mapper.selectPage(
                TenantSubsystemEnabledDO::getTenantId, 1L,
                TenantSubsystemEnabledDO::getEnabled, true, 1, 10);
        assertThat(tenantEnabledPage.getList()).hasSize(2);
        assertThat(tenantEnabledPage.getTotal()).isEqualTo(2L);
    }

    @Test
    void shouldUpdateById() {
        TenantSubsystemEnabledDO enabled = mapper.selectOne(TenantSubsystemEnabledDO::getTenantId, 2L,
                TenantSubsystemEnabledDO::getSubsystemId, 1);
        enabled.setEnabled(true);
        enabled.setUpdateTime(LocalDateTime.now());

        assertThat(mapper.updateById(enabled)).isEqualTo(1);
        assertThat(mapper.selectById(enabled.getId()).getEnabled()).isTrue();
    }

    @Test
    void shouldDeleteByFieldWithTableLogic() {
        assertThat(mapper.delete(TenantSubsystemEnabledDO::getTenantId, 2L)).isEqualTo(1);

        Boolean deleted = new JdbcTemplate(dataSource).queryForObject(
                "SELECT deleted FROM tenant_subsystem_enabled WHERE tenant_id = ? AND subsystem_id = ?",
                Boolean.class, 2L, 1);
        assertThat(deleted).isTrue();
        assertThat(mapper.selectOne(TenantSubsystemEnabledDO::getTenantId, 2L,
                TenantSubsystemEnabledDO::getSubsystemId, 1)).isNull();
        assertThat(mapper.selectList()).hasSize(2);
    }

    private static TenantSubsystemEnabledDO newEnabled(Long tenantId, Integer subsystemId, Boolean enabled) {
        TenantSubsystemEnabledDO tenantSubsystemEnabled = new TenantSubsystemEnabledDO();
        tenantSubsystemEnabled.setTenantId(tenantId);
        tenantSubsystemEnabled.setSubsystemId(subsystemId);
        tenantSubsystemEnabled.setEnabled(enabled);
        tenantSubsystemEnabled.setEnableTime(LocalDateTime.now());
        tenantSubsystemEnabled.setCreator("h5");
        tenantSubsystemEnabled.setCreateTime(LocalDateTime.now());
        tenantSubsystemEnabled.setUpdater("h5");
        tenantSubsystemEnabled.setUpdateTime(LocalDateTime.now());
        tenantSubsystemEnabled.setDeleted(false);
        return tenantSubsystemEnabled;
    }
}
