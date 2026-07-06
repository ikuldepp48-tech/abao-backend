package com.geihou.module.system.dal.mysql.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.geihou.common.pojo.PageResult;
import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.module.system.dal.dataobject.tenant.TenantDO;
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
        classes = TenantMapperDbSmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_system_tenant_mapper_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class TenantMapperDbSmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import(GeihouMyBatisAutoConfiguration.class)
    @MapperScan("com.geihou.module.system.dal.mysql.tenant")
    static class TestConfig {
    }

    @Autowired
    private TenantMapper mapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MybatisPlusInterceptor mybatisPlusInterceptor;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS tenants");
        jdbcTemplate.execute("""
                CREATE TABLE tenants (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    tenant_code VARCHAR(32) NOT NULL,
                    tenant_name VARCHAR(128) NOT NULL,
                    merchant_type VARCHAR(8) NOT NULL DEFAULT 'B',
                    status VARCHAR(20) NOT NULL DEFAULT 'TRIAL',
                    life_stage VARCHAR(20) NOT NULL DEFAULT 'STARTUP',
                    contract_start_date DATE,
                    contract_end_date DATE,
                    business_day_cutoff_hour TINYINT NOT NULL DEFAULT 3,
                    timezone VARCHAR(32) NOT NULL DEFAULT 'Asia/Shanghai',
                    contact_name VARCHAR(64),
                    contact_phone VARCHAR(32),
                    creator VARCHAR(64) NOT NULL DEFAULT '',
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updater VARCHAR(64) NOT NULL DEFAULT '',
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_code, tenant_name, merchant_type, status, life_stage,
                    business_day_cutoff_hour, timezone, creator, updater)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "TC001", "Alpha", "B", "ACTIVE", "STARTUP", 3, "Asia/Shanghai", "h5", "h5");
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_code, tenant_name, merchant_type, status, life_stage,
                    business_day_cutoff_hour, timezone, creator, updater)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "TC002", "Beta", "B", "ACTIVE", "DEVELOPMENT", 3, "Asia/Shanghai", "h5", "h5");
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_code, tenant_name, merchant_type, status, life_stage,
                    business_day_cutoff_hour, timezone, creator, updater)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "TC003", "Gamma", "A", "SUSPENDED", "MATURE", 4, "Asia/Shanghai", "h5", "h5");
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
        TenantDO tenant = newTenant("TC004", "Delta", "ACTIVE");

        assertThat(mapper.insert(tenant)).isEqualTo(1);
        assertThat(tenant.getId()).isNotNull();

        TenantDO selected = mapper.selectById(tenant.getId());
        assertThat(selected.getTenantCode()).isEqualTo("TC004");
        assertThat(selected.getTenantName()).isEqualTo("Delta");
    }

    @Test
    void shouldSelectOneBySingleField() {
        TenantDO tenant = mapper.selectOne(TenantDO::getTenantCode, "TC001");

        assertThat(tenant).isNotNull();
        assertThat(tenant.getTenantName()).isEqualTo("Alpha");
    }

    @Test
    void shouldSelectOneByTwoFields() {
        TenantDO tenant = mapper.selectOne(TenantDO::getTenantCode, "TC002", TenantDO::getStatus, "ACTIVE");

        assertThat(tenant).isNotNull();
        assertThat(tenant.getTenantName()).isEqualTo("Beta");
    }

    @Test
    void shouldSelectListsAndCounts() {
        assertThat(mapper.selectList()).hasSize(3);
        assertThat(mapper.selectList(TenantDO::getStatus, "ACTIVE")).hasSize(2);
        assertThat(mapper.selectList(TenantDO::getStatus, "ACTIVE", TenantDO::getMerchantType, "B")).hasSize(2);
        assertThat(mapper.selectCount(TenantDO::getStatus, "SUSPENDED")).isEqualTo(1L);
    }

    @Test
    void shouldSelectPages() {
        PageResult<TenantDO> firstPage = mapper.selectPage(1, 2);
        assertThat(firstPage.getList()).hasSize(2);
        assertThat(firstPage.getTotal()).isEqualTo(3L);
        assertThat(firstPage.getPageNo()).isEqualTo(1);
        assertThat(firstPage.getPageSize()).isEqualTo(2);

        PageResult<TenantDO> orderedPage = mapper.selectPage(1, 2,
                new LambdaQueryWrapper<TenantDO>().orderByAsc(TenantDO::getTenantCode));
        assertThat(orderedPage.getList()).extracting(TenantDO::getTenantCode).containsExactly("TC001", "TC002");

        PageResult<TenantDO> activePage = mapper.selectPage(TenantDO::getStatus, "ACTIVE", 1, 10);
        assertThat(activePage.getList()).hasSize(2);
        assertThat(activePage.getTotal()).isEqualTo(2L);

        PageResult<TenantDO> activeBPage = mapper.selectPage(TenantDO::getStatus, "ACTIVE",
                TenantDO::getMerchantType, "B", 1, 10);
        assertThat(activeBPage.getList()).hasSize(2);
        assertThat(activeBPage.getTotal()).isEqualTo(2L);
    }

    @Test
    void shouldUpdateById() {
        TenantDO tenant = mapper.selectOne(TenantDO::getTenantCode, "TC001");
        tenant.setTenantName("Alpha Renamed");
        tenant.setUpdateTime(LocalDateTime.now());

        assertThat(mapper.updateById(tenant)).isEqualTo(1);
        assertThat(mapper.selectById(tenant.getId()).getTenantName()).isEqualTo("Alpha Renamed");
    }

    @Test
    void shouldDeleteByFieldWithTableLogic() {
        assertThat(mapper.delete(TenantDO::getTenantCode, "TC003")).isEqualTo(1);

        Boolean deleted = new JdbcTemplate(dataSource)
                .queryForObject("SELECT deleted FROM tenants WHERE tenant_code = ?", Boolean.class, "TC003");
        assertThat(deleted).isTrue();
        assertThat(mapper.selectOne(TenantDO::getTenantCode, "TC003")).isNull();
        assertThat(mapper.selectList()).hasSize(2);
    }

    private static TenantDO newTenant(String tenantCode, String tenantName, String status) {
        TenantDO tenant = new TenantDO();
        tenant.setTenantCode(tenantCode);
        tenant.setTenantName(tenantName);
        tenant.setMerchantType("B");
        tenant.setStatus(status);
        tenant.setLifeStage("STARTUP");
        tenant.setBusinessDayCutoffHour(3);
        tenant.setTimezone("Asia/Shanghai");
        tenant.setCreator("h5");
        tenant.setCreateTime(LocalDateTime.now());
        tenant.setUpdater("h5");
        tenant.setUpdateTime(LocalDateTime.now());
        tenant.setDeleted(false);
        return tenant;
    }
}
