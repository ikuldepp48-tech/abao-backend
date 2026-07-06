package com.geihou.framework.mybatis.core.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.geihou.common.pojo.PageResult;
import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.framework.mybatis.test.TestDataObject;
import com.geihou.framework.mybatis.test.mapper.TestDataObjectMapper;
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
        classes = BaseMapperXDbSmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_mybatis_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class BaseMapperXDbSmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import(GeihouMyBatisAutoConfiguration.class)
    @MapperScan("com.geihou.framework.mybatis.test.mapper")
    static class TestConfig {
    }

    @Autowired
    private TestDataObjectMapper mapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MybatisPlusInterceptor mybatisPlusInterceptor;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mybatis_test_data (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    status VARCHAR(32) NOT NULL
                )
                """);
        jdbcTemplate.execute("DELETE FROM mybatis_test_data");
        jdbcTemplate.update("INSERT INTO mybatis_test_data (name, status) VALUES (?, ?)", "alpha", "ACTIVE");
        jdbcTemplate.update("INSERT INTO mybatis_test_data (name, status) VALUES (?, ?)", "beta", "ACTIVE");
        jdbcTemplate.update("INSERT INTO mybatis_test_data (name, status) VALUES (?, ?)", "gamma", "INACTIVE");
    }

    @Test
    void shouldRegisterPaginationInterceptorInDbSmokeContext() {
        assertThat(mybatisPlusInterceptor.getInterceptors())
                .singleElement()
                .isInstanceOfSatisfying(PaginationInnerInterceptor.class, pagination ->
                        assertThat(pagination.getDbType()).isEqualTo(DbType.MYSQL));
    }

    @Test
    void selectPageShouldReturnFirstPageFromRealDatabase() {
        PageResult<TestDataObject> result = mapper.selectPage(1, 2,
                new LambdaQueryWrapper<TestDataObject>().orderByAsc(TestDataObject::getId));

        assertThat(result.getList()).extracting(TestDataObject::getName).containsExactly("alpha", "beta");
        assertThat(result.getTotal()).isEqualTo(3L);
        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(2);
    }

    @Test
    void selectPageShouldReturnSecondPageFromRealDatabase() {
        PageResult<TestDataObject> result = mapper.selectPage(2, 2,
                new LambdaQueryWrapper<TestDataObject>().orderByAsc(TestDataObject::getId));

        assertThat(result.getList()).extracting(TestDataObject::getName).containsExactly("gamma");
        assertThat(result.getTotal()).isEqualTo(3L);
        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(2);
    }

    @Test
    void selectPageShouldApplyConditionThroughRealDatabase() {
        PageResult<TestDataObject> result = mapper.selectPage(
                TestDataObject::getStatus, "ACTIVE", 1, 10);

        assertThat(result.getList()).extracting(TestDataObject::getName)
                .containsExactlyInAnyOrder("alpha", "beta");
        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
    }
}
