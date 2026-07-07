package com.geihou.bootstrap;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Testcontainers
class FlywayMigrationSmokeTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("geihou_smoke")
            .withUsername("geihou")
            .withPassword("geihou");

    @Test
    void migratesGroup0SqlCandidatesOnMySql() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .load();

        MigrateResult result = flyway.migrate();

        // The test classpath includes bootstrap migrations plus module test-scope migrations.
        // Do not pin this to an old exact count; verify the current classpath migrates cleanly.
        assertThat(result.migrationsExecuted).isPositive();

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertCount(connection,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                    result.migrationsExecuted);

            // Existing table count assertions.
            assertCount(connection, "SELECT COUNT(*) FROM tenants", 1);
            assertCount(connection, "SELECT COUNT(*) FROM tenant_subsystem_enabled", 11);
            assertCount(connection, "SELECT COUNT(*) FROM microservice_registry", 12);
            // H173: market/strategy removed by V03_029 to align with H159 12-service topology.
            assertCount(connection,
                    "SELECT COUNT(*) FROM microservice_registry "
                            + "WHERE service_name IN ('geihou-module-market','geihou-module-strategy')",
                    0);
            // H173: strategy-track is a different service from strategy and must remain.
            assertCount(connection,
                    "SELECT COUNT(*) FROM microservice_registry "
                            + "WHERE service_name = 'geihou-module-strategy-track'",
                    1);
            assertCount(connection, "SELECT COUNT(*) FROM service_health_log", 0);
            assertCount(connection, "SELECT COUNT(*) FROM auth_token_revoked", 0);
            assertCount(connection, "SELECT COUNT(*) FROM auth_user", 0);

            // H144: four RBAC tables, exact 11 role seeds, 21 permissions and 40 grants.
            assertCount(connection, "SELECT COUNT(*) FROM auth_role", 11);
            assertCount(connection, "SELECT COUNT(*) FROM auth_user_role", 0);
            assertCount(connection, "SELECT COUNT(*) FROM auth_permission", 21);
            assertCount(connection, "SELECT COUNT(*) FROM auth_role_permission", 40);
            assertH144PermissionSeeds(connection);
            assertH144RolePermissionSeeds(connection);

            // H142: exact 3 platform + 8 tenant-template codes.
            assertCount(connection,
                    "SELECT COUNT(*) FROM auth_role WHERE role_code IN "
                            + "('PLATFORM_OPERATOR','CONSULTANT','PLATFORM_DEVELOPER')",
                    3);
            assertCount(connection,
                    "SELECT COUNT(*) FROM auth_role WHERE role_code IN "
                            + "('OWNER','SHOP_MANAGER','CK_MANAGER','CASHIER','WAITER',"
                            + "'KITCHEN_COOK','CK_WORKER','CUSTOMER')",
                    8);

            // H142: exact is_admin / is_builtin / status / tenant values.
            assertCount(connection,
                    "SELECT COUNT(*) FROM auth_role WHERE tenant_id = 0 AND is_builtin = 1 AND status = 'ACTIVE'",
                    11);
            assertCount(connection,
                    "SELECT COUNT(*) FROM auth_role WHERE is_admin = 1",
                    3);
            assertCount(connection,
                    "SELECT COUNT(*) FROM auth_role WHERE role_code = 'PLATFORM_OPERATOR' AND is_admin = 1",
                    1);
            assertCount(connection,
                    "SELECT COUNT(*) FROM auth_role WHERE role_code = 'CONSULTANT' AND is_admin = 0",
                    1);
            assertCount(connection,
                    "SELECT COUNT(*) FROM auth_role WHERE role_code = 'PLATFORM_DEVELOPER' AND is_admin = 1",
                    1);
            assertCount(connection,
                    "SELECT COUNT(*) FROM auth_role WHERE role_code = 'OWNER' AND is_admin = 1",
                    1);
            assertCount(connection,
                    "SELECT COUNT(*) FROM auth_role WHERE is_admin = 0 AND role_code IN "
                            + "('SHOP_MANAGER','CK_MANAGER','CASHIER','WAITER',"
                            + "'KITCHEN_COOK','CK_WORKER','CUSTOMER')",
                    7);

            // H142: no ROLE_ prefix in any role seed.
            assertCount(connection,
                    "SELECT COUNT(*) FROM auth_role WHERE role_code LIKE 'ROLE_%'",
                    0);

            // H142: information_schema confirms auth_permission has NO tenant_id column.
            assertThat(columnExists(connection, "geihou_smoke", "auth_permission", "tenant_id"))
                    .as("auth_permission must not have tenant_id column")
                    .isFalse();
            // sanity: the other three tables do have tenant_id.
            assertThat(columnExists(connection, "geihou_smoke", "auth_role", "tenant_id"))
                    .as("auth_role must have tenant_id column")
                    .isTrue();
            assertThat(columnExists(connection, "geihou_smoke", "auth_user_role", "tenant_id"))
                    .as("auth_user_role must have tenant_id column")
                    .isTrue();
            assertThat(columnExists(connection, "geihou_smoke", "auth_role_permission", "tenant_id"))
                    .as("auth_role_permission must have tenant_id column")
                    .isTrue();

            // H142: expected STORED generated columns exist.
            assertThat(generatedColumnExists(connection, "geihou_smoke", "auth_role", "active_role_code"))
                    .as("auth_role.active_role_code generated column must exist")
                    .isTrue();
            assertThat(generatedColumnExists(connection, "geihou_smoke", "auth_user_role", "active_user_id"))
                    .as("auth_user_role.active_user_id generated column must exist")
                    .isTrue();
            assertThat(generatedColumnExists(connection, "geihou_smoke", "auth_user_role", "active_role_id"))
                    .as("auth_user_role.active_role_id generated column must exist")
                    .isTrue();
            assertThat(generatedColumnExists(connection, "geihou_smoke", "auth_permission", "active_permission_code"))
                    .as("auth_permission.active_permission_code generated column must exist")
                    .isTrue();
            assertThat(generatedColumnExists(connection, "geihou_smoke", "auth_role_permission", "active_role_id"))
                    .as("auth_role_permission.active_role_id generated column must exist")
                    .isTrue();
            assertThat(generatedColumnExists(connection, "geihou_smoke", "auth_role_permission", "active_permission_id"))
                    .as("auth_role_permission.active_permission_id generated column must exist")
                    .isTrue();

            // H142: expected unique indexes on generated columns exist.
            assertThat(indexExists(connection, "geihou_smoke", "auth_role", "uk_tenant_active_role_code"))
                    .as("uk_tenant_active_role_code must exist")
                    .isTrue();
            assertThat(indexExists(connection, "geihou_smoke", "auth_user_role", "uk_tenant_active_user_role"))
                    .as("uk_tenant_active_user_role must exist")
                    .isTrue();
            assertThat(indexExists(connection, "geihou_smoke", "auth_permission", "uk_active_permission_code"))
                    .as("uk_active_permission_code must exist")
                    .isTrue();
            assertThat(indexExists(connection, "geihou_smoke", "auth_role_permission", "uk_tenant_active_role_permission"))
                    .as("uk_tenant_active_role_permission must exist")
                    .isTrue();

            // H142: PRD ordinary indexes preserved.
            assertThat(indexExists(connection, "geihou_smoke", "auth_role", "idx_status"))
                    .as("auth_role.idx_status must exist").isTrue();
            assertThat(indexExists(connection, "geihou_smoke", "auth_user_role", "idx_role"))
                    .as("auth_user_role.idx_role must exist").isTrue();
            assertThat(indexExists(connection, "geihou_smoke", "auth_permission", "idx_subsystem_module"))
                    .as("auth_permission.idx_subsystem_module must exist").isTrue();
            assertThat(indexExists(connection, "geihou_smoke", "auth_permission", "idx_risk_level"))
                    .as("auth_permission.idx_risk_level must exist").isTrue();
            assertThat(indexExists(connection, "geihou_smoke", "auth_role_permission", "idx_permission"))
                    .as("auth_role_permission.idx_permission must exist").isTrue();

            // H142: no physical FK / reference constraints on the four tables.
            assertNoPhysicalForeignKeys(connection, "geihou_smoke");

            // H142: lifecycle behavior for each of the four tables —
            // first active insert succeeds; duplicate active insert rejected;
            // first soft delete succeeds; recreate same key succeeds; second soft delete succeeds.
            verifyGeneratedColumnLifecycle(connection);

            // H148: audit table exists with correct structure.
            assertH148AuditTable(connection);

            // H142: mismatch / orphan counts zero on migrated seed state.
            // auth_role rows whose tenant_id != 0 in seed (should be 0 — all seeds at tenant 0).
            assertCount(connection,
                    "SELECT COUNT(*) FROM auth_role WHERE tenant_id <> 0",
                    0);
            // auth_user_role orphan (role_id not pointing to any auth_role).
            assertCount(connection,
                    "SELECT COUNT(*) FROM auth_user_role aur "
                            + "LEFT JOIN auth_role ar ON aur.role_id = ar.id "
                            + "WHERE ar.id IS NULL",
                    0);
            // auth_role_permission orphan (role_id or permission_id dangling).
            assertCount(connection,
                    "SELECT COUNT(*) FROM auth_role_permission arp "
                            + "LEFT JOIN auth_role ar ON arp.role_id = ar.id "
                            + "LEFT JOIN auth_permission ap ON arp.permission_id = ap.id "
                            + "WHERE ar.id IS NULL OR ap.id IS NULL",
                    0);
        }
    }

    private static void assertCount(Connection connection, String sql, int expected) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(expected);
        }
    }

    private static void assertH144PermissionSeeds(Connection connection) throws Exception {
        assertCount(connection,
                "SELECT COUNT(*) FROM auth_permission WHERE deleted = 0 "
                        + "AND permission_code REGEXP '^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*){1,2}$'",
                21);
        assertCount(connection,
                "SELECT COUNT(*) FROM auth_permission WHERE permission_code IN "
                        + "('microservice:read','ORDER:READ','FINANCE:WRITE','LEAD_DEVELOPER',"
                        + "'OWNER','CONSULTANT','CUSTOMER','PLATFORM_OPERATOR','TENANT_NOT_FOUND')",
                0);
        assertCount(connection, "SELECT COUNT(*) FROM auth_permission WHERE risk_level = 'HIGH'", 1);
        assertCount(connection, "SELECT COUNT(*) FROM auth_permission WHERE risk_level = 'MEDIUM'", 14);
        assertCount(connection, "SELECT COUNT(*) FROM auth_permission WHERE risk_level = 'LOW'", 6);

        assertPermissionSeed(connection, "finance:core-profit:read", "核心利润计算引擎-读取", 1,
                "finance-core-profit", "core-profit", "read", "MEDIUM", "核心利润计算引擎读取权限");
        assertPermissionSeed(connection, "cart:staff-assisted", "代客下单权限", 1,
                "cart", "cart", "staff-assisted", "MEDIUM", "员工代客下单权限(购物车辅助结算)");
        assertPermissionSeed(connection, "ck:dashboard:read", "中央厨房仪表盘-读取", 2,
                "ck-dashboard", "dashboard", "read", "MEDIUM", "日产能 / 当日成本 / 配送进度 / 告警");
        assertPermissionSeed(connection, "ck:procurement:read", "集中采购管理-读取", 2,
                "ck-procurement", "procurement", "read", "MEDIUM", "采购单 / 阶梯协议 / 比价 / 验收 / 付款");
        assertPermissionSeed(connection, "ck:material:read", "原料档案+批次+保质期-读取", 2,
                "ck-material", "material", "read", "LOW", "原料 SKU / 批次追溯 / 保质期预警");
        assertPermissionSeed(connection, "supplier:read", "供应商管理+8维度画像-读取", 2,
                "supplier", "supplier", "read", "LOW", "入口跳到组 4 实现的供应商画像");
        assertPermissionSeed(connection, "bom:read", "半成品BOM配方-读取", 2,
                "bom", "bom", "read", "MEDIUM", "成品→半成品→原料层级 BOM / 损耗率");
        assertPermissionSeed(connection, "production:write", "半成品制作登记-写入", 2,
                "production", "production", "write", "MEDIUM", "工单 / 原料投入 / 产出 / 损耗 / 工时");
        assertPermissionSeed(connection, "cost:read", "中央厨房成本归集-读取", 1,
                "cost", "cost", "read", "MEDIUM", "双层归集:CK 内部 + 配送到门店");
        assertPermissionSeed(connection, "employee:read", "中央厨房员工管理-读取", 3,
                "employee", "employee", "read", "MEDIUM", "员工双形态(C5)/ 排班 / 产能 / 提成");
        assertPermissionSeed(connection, "delivery:write", "门店调拨配送-写入", 2,
                "delivery", "delivery", "write", "MEDIUM", "配送计划 / 出库 / 跟踪 / 门店收货 / 配送成本");
        assertPermissionSeed(connection, "iot:read", "CK IoT监控-读取", 6,
                "iot", "iot", "read", "LOW", "冰箱温度 / 烤箱 / 食安 / 能耗");
        assertPermissionSeed(connection, "decision:read", "CK决策管理-读取", 5,
                "decision", "decision", "read", "MEDIUM", "决策记录 / OKR / 任务下发(子系统 5)");
        assertPermissionSeed(connection, "ck:settings:write", "CK系统设置-写入", null,
                "ck-settings", "settings", "write", "HIGH", "CK 基础配置 / 装修 / 模块开关");
        assertPermissionSeed(connection, "consultant:dashboard:read", "咨询师仪表盘-读取", 9,
                "consultant-dashboard", "dashboard", "read", "MEDIUM", "我的客户 / 完整度排行 / 今日任务 / 告警");
        assertPermissionSeed(connection, "consultant:client:read", "客户列表与档案-读取", null,
                "consultant-client", "client", "read", "MEDIUM", "客户列表与档案-读取(跨飞轮/洞察子系统)");
        assertPermissionSeed(connection, "consultant:diagnosis:read", "5步生存链诊断-读取", 7,
                "consultant-diagnosis", "diagnosis", "read", "MEDIUM", "5 步生存链断点识别 + 改善建议");
        assertPermissionSeed(connection, "consultant:strategy:write", "战略画像设计-写入", 5,
                "consultant-strategy", "strategy", "write", "MEDIUM", "5 因素 × 公共方法论 → 独特战略推导");
        assertPermissionSeed(connection, "consultant:case:read", "案例库-读取", 9,
                "consultant-case", "case", "read", "LOW", "真实客户脱敏案例 + 检索 + 标签");
        assertPermissionSeed(connection, "consultant:methodology:read", "方法论库-读取", 9,
                "consultant-methodology", "methodology", "read", "LOW", "41 份原始方法论 + 持续迭代");
        assertPermissionSeed(connection, "consultant:benchmark:read", "行业基准对照-读取", 4,
                "consultant-benchmark", "benchmark", "read", "LOW", "跨租户脱敏聚合 / P25-P90 分位 / 同行业对照");
    }

    private static void assertPermissionSeed(Connection connection, String code, String name, Integer subsystemId,
                                             String module, String resource, String action, String risk,
                                             String description) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM auth_permission WHERE deleted = 0 AND permission_code = ? "
                        + "AND permission_name = ? AND subsystem_id <=> ? AND module_name = ? "
                        + "AND resource = ? AND action = ? AND risk_level = ? AND description = ?")) {
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setObject(3, subsystemId);
            ps.setString(4, module);
            ps.setString(5, resource);
            ps.setString(6, action);
            ps.setString(7, risk);
            ps.setString(8, description);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).as("exact permission seed %s", code).isEqualTo(1);
            }
        }
    }

    private static void assertH144RolePermissionSeeds(Connection connection) throws Exception {
        assertCount(connection,
                "SELECT COUNT(*) FROM auth_role_permission WHERE deleted = 0 AND tenant_id <> 0", 0);
        assertRoleGrantCount(connection, "OWNER", 14);
        assertRoleGrantCount(connection, "SHOP_MANAGER", 1);
        assertRoleGrantCount(connection, "CK_MANAGER", 12);
        assertRoleGrantCount(connection, "CASHIER", 1);
        assertRoleGrantCount(connection, "WAITER", 0);
        assertRoleGrantCount(connection, "KITCHEN_COOK", 0);
        assertRoleGrantCount(connection, "CK_WORKER", 4);
        assertRoleGrantCount(connection, "CUSTOMER", 0);
        assertRoleGrantCount(connection, "CONSULTANT", 8);
        assertRoleGrantCount(connection, "PLATFORM_OPERATOR", 0);
        assertRoleGrantCount(connection, "PLATFORM_DEVELOPER", 0);

        assertPermissionGrantCount(connection, "finance:core-profit:read", 2);
        assertPermissionGrantCount(connection, "cart:staff-assisted", 3);
        assertPermissionGrantCount(connection, "ck:dashboard:read", 3);
        assertPermissionGrantCount(connection, "ck:procurement:read", 2);
        assertPermissionGrantCount(connection, "ck:material:read", 3);
        assertPermissionGrantCount(connection, "supplier:read", 2);
        assertPermissionGrantCount(connection, "bom:read", 3);
        assertPermissionGrantCount(connection, "production:write", 3);
        assertPermissionGrantCount(connection, "cost:read", 2);
        assertPermissionGrantCount(connection, "employee:read", 2);
        assertPermissionGrantCount(connection, "delivery:write", 2);
        assertPermissionGrantCount(connection, "iot:read", 2);
        assertPermissionGrantCount(connection, "decision:read", 2);
        assertPermissionGrantCount(connection, "ck:settings:write", 2);
        assertPermissionGrantCount(connection, "consultant:dashboard:read", 1);
        assertPermissionGrantCount(connection, "consultant:client:read", 1);
        assertPermissionGrantCount(connection, "consultant:diagnosis:read", 1);
        assertPermissionGrantCount(connection, "consultant:strategy:write", 1);
        assertPermissionGrantCount(connection, "consultant:case:read", 1);
        assertPermissionGrantCount(connection, "consultant:methodology:read", 1);
        assertPermissionGrantCount(connection, "consultant:benchmark:read", 1);

        assertGrant(connection, "OWNER", "finance:core-profit:read");
        assertGrant(connection, "OWNER", "cart:staff-assisted");
        assertAllCkGrants(connection, "OWNER");
        assertGrant(connection, "SHOP_MANAGER", "cart:staff-assisted");
        assertAllCkGrants(connection, "CK_MANAGER");
        assertGrant(connection, "CASHIER", "cart:staff-assisted");
        assertGrant(connection, "CK_WORKER", "ck:dashboard:read");
        assertGrant(connection, "CK_WORKER", "ck:material:read");
        assertGrant(connection, "CK_WORKER", "bom:read");
        assertGrant(connection, "CK_WORKER", "production:write");
        assertGrant(connection, "CONSULTANT", "finance:core-profit:read");
        assertAllConsultantGrants(connection);

        assertCount(connection,
                "SELECT COUNT(*) FROM auth_role_permission arp "
                        + "JOIN auth_role ar ON ar.id = arp.role_id AND ar.deleted = 0 "
                        + "JOIN auth_permission ap ON ap.id = arp.permission_id AND ap.deleted = 0 "
                        + "WHERE arp.deleted = 0 AND arp.tenant_id = 0 "
                        + "AND ap.permission_code LIKE 'consultant:%' AND ar.role_code <> 'CONSULTANT'",
                0);
        assertCount(connection,
                "SELECT COUNT(*) FROM auth_role_permission arp "
                        + "JOIN auth_role ar ON ar.id = arp.role_id AND ar.deleted = 0 "
                        + "JOIN auth_permission ap ON ap.id = arp.permission_id AND ap.deleted = 0 "
                        + "WHERE arp.deleted = 0 AND (ar.tenant_id <> 0 OR ar.is_builtin <> 1 "
                        + "OR ar.status <> 'ACTIVE')",
                0);
    }

    private static void assertRoleGrantCount(Connection connection, String roleCode, int expected) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM auth_role_permission arp "
                        + "JOIN auth_role ar ON ar.id = arp.role_id "
                        + "WHERE arp.deleted = 0 AND arp.tenant_id = 0 AND ar.deleted = 0 "
                        + "AND ar.tenant_id = 0 AND ar.role_code = ?")) {
            ps.setString(1, roleCode);
            assertSingleCount(ps, expected, "grant count for role " + roleCode);
        }
    }

    private static void assertPermissionGrantCount(Connection connection, String permissionCode, int expected)
            throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM auth_role_permission arp "
                        + "JOIN auth_permission ap ON ap.id = arp.permission_id "
                        + "WHERE arp.deleted = 0 AND arp.tenant_id = 0 AND ap.deleted = 0 "
                        + "AND ap.permission_code = ?")) {
            ps.setString(1, permissionCode);
            assertSingleCount(ps, expected, "grant count for permission " + permissionCode);
        }
    }

    private static void assertGrant(Connection connection, String roleCode, String permissionCode) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM auth_role_permission arp "
                        + "JOIN auth_role ar ON ar.id = arp.role_id "
                        + "JOIN auth_permission ap ON ap.id = arp.permission_id "
                        + "WHERE arp.deleted = 0 AND arp.tenant_id = 0 "
                        + "AND ar.deleted = 0 AND ar.tenant_id = 0 AND ar.role_code = ? "
                        + "AND ap.deleted = 0 AND ap.permission_code = ?")) {
            ps.setString(1, roleCode);
            ps.setString(2, permissionCode);
            assertSingleCount(ps, 1, roleCode + " -> " + permissionCode);
        }
    }

    private static void assertAllCkGrants(Connection connection, String roleCode) throws Exception {
        String[] codes = {"ck:dashboard:read", "ck:procurement:read", "ck:material:read", "supplier:read",
                "bom:read", "production:write", "cost:read", "employee:read", "delivery:write", "iot:read",
                "decision:read", "ck:settings:write"};
        for (String code : codes) {
            assertGrant(connection, roleCode, code);
        }
    }

    private static void assertAllConsultantGrants(Connection connection) throws Exception {
        String[] codes = {"consultant:dashboard:read", "consultant:client:read", "consultant:diagnosis:read",
                "consultant:strategy:write", "consultant:case:read", "consultant:methodology:read",
                "consultant:benchmark:read"};
        for (String code : codes) {
            assertGrant(connection, "CONSULTANT", code);
        }
    }

    private static void assertSingleCount(PreparedStatement ps, int expected, String description) throws Exception {
        try (ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as(description).isEqualTo(expected);
        }
    }

    // H148: assert audit table structure and append-only design.
    private static void assertH148AuditTable(Connection connection) throws Exception {
        // Table exists and is empty (fresh migration, no provisioning attempts).
        assertCount(connection, "SELECT COUNT(*) FROM auth_rbac_provisioning_audit", 0);

        // H148: no 'deleted' column (append-only, no soft-delete).
        assertThat(columnExists(connection, "geihou_smoke", "auth_rbac_provisioning_audit", "deleted"))
                .as("auth_rbac_provisioning_audit must not have 'deleted' column (append-only)")
                .isFalse();

        // H148: expected columns exist.
        String[] expectedColumns = {
                "id", "attempt_id", "tenant_id", "merchant_type", "outcome",
                "roles_expected", "roles_inserted", "roles_skipped",
                "grants_expected", "grants_inserted", "grants_skipped", "grants_preserved",
                "error_class", "error_detail", "triggered_by",
                "creator", "create_time", "updater", "update_time"
        };
        for (String col : expectedColumns) {
            assertThat(columnExists(connection, "geihou_smoke", "auth_rbac_provisioning_audit", col))
                    .as("auth_rbac_provisioning_audit must have column %s", col)
                    .isTrue();
        }

        // H148: no generated/stored columns.
        assertThat(generatedColumnExists(connection, "geihou_smoke", "auth_rbac_provisioning_audit", "id"))
                .as("auth_rbac_provisioning_audit must have no stored generated columns")
                .isFalse();

        // H148: no physical FK.
        assertThat(indexExists(connection, "geihou_smoke", "auth_rbac_provisioning_audit", "idx_attempt"))
                .as("idx_attempt index must exist")
                .isTrue();
        assertThat(indexExists(connection, "geihou_smoke", "auth_rbac_provisioning_audit", "idx_tenant_outcome"))
                .as("idx_tenant_outcome index must exist")
                .isTrue();
        assertThat(indexExists(connection, "geihou_smoke", "auth_rbac_provisioning_audit", "idx_create_time"))
                .as("idx_create_time index must exist")
                .isTrue();

        // H148: no physical FK references on audit table.
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_schema = ? AND table_name = 'auth_rbac_provisioning_audit' "
                        + "AND referenced_table_name IS NOT NULL")) {
            ps.setString(1, "geihou_smoke");
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("auth_rbac_provisioning_audit must have no physical FK references")
                        .isZero();
            }
        }
    }

    private static boolean columnExists(Connection connection, String schema, String table, String column)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean generatedColumnExists(Connection connection, String schema, String table, String column)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ? AND column_name = ? "
                        + "AND GENERATION_EXPRESSION IS NOT NULL "
                        + "AND TRIM(GENERATION_EXPRESSION) <> '' "
                        + "AND EXTRA = 'STORED GENERATED'")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean indexExists(Connection connection, String schema, String table, String indexName)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM information_schema.statistics "
                        + "WHERE table_schema = ? AND table_name = ? AND index_name = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void assertNoPhysicalForeignKeys(Connection connection, String schema) throws SQLException {
        String[] tables = {"auth_role", "auth_user_role", "auth_permission", "auth_role_permission"};
        for (String table : tables) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.key_column_usage "
                            + "WHERE table_schema = ? AND table_name = ? "
                            + "AND referenced_table_name IS NOT NULL")) {
                ps.setString(1, schema);
                ps.setString(2, table);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1))
                            .as("table %s must have no physical FK references", table)
                            .isZero();
                }
            }
        }
    }

    private static void verifyGeneratedColumnLifecycle(Connection connection) throws Exception {
        connection.setAutoCommit(false);
        try {
            // auth_role lifecycle: use a dedicated tenant (999) + role code to avoid seed conflicts.
            lifecycleAuthRole(connection);
            // auth_user_role lifecycle.
            lifecycleAuthUserRole(connection);
            // auth_permission lifecycle.
            lifecycleAuthPermission(connection);
            // auth_role_permission lifecycle.
            lifecycleAuthRolePermission(connection);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @FunctionalInterface
    private interface SqlAction {
        void execute() throws Exception;
    }

    /**
     * Proves the captured SQLException is specifically a MySQL duplicate-key violation.
     * SQLState must be 23000 (or start with 23) and vendor error code must be 1062.
     * Any non-duplicate SQLException is rethrown so the test fails on the original cause.
     */
    private static void assertMySqlDuplicateKey(SQLException e) throws SQLException {
        String sqlState = e.getSQLState();
        int vendorCode = e.getErrorCode();
        boolean sqlStateOk = sqlState != null
                && (sqlState.equals("23000") || sqlState.startsWith("23"));
        boolean vendorCodeOk = vendorCode == 1062;
        if (!sqlStateOk || !vendorCodeOk) {
            throw e;
        }
    }

    /**
     * Runs the supplied insert action and commits. The action is expected to violate the
     * unique generated-column index. If no exception is raised, the test fails. If a
     * SQLException is raised, it is verified to be a MySQL duplicate-key error (1062/23000);
     * otherwise the original exception is rethrown. The connection is rolled back after the
     * expected duplicate-key failure so subsequent lifecycle steps start clean.
     */
    private static void assertDuplicateRejected(Connection connection, SqlAction action, String message)
            throws Exception {
        try {
            action.execute();
            connection.commit();
            fail(message);
        } catch (SQLException expected) {
            connection.rollback();
            assertMySqlDuplicateKey(expected);
        }
    }

    /**
     * Resolves the runtime id of the seeded PLATFORM_OPERATOR role (tenant 0).
     * H142 review: do not assume id=1; the migration seeds roles in a specific order,
     * so the id must be derived from the migrated state.
     */
    private static long resolvePlatformOperatorRoleId(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM auth_role WHERE role_code='PLATFORM_OPERATOR' "
                        + "AND tenant_id=0 AND deleted=0")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("PLATFORM_OPERATOR role must exist in migrated seed state")
                        .isTrue();
                long id = rs.getLong(1);
                assertThat(id).as("PLATFORM_OPERATOR role id must be positive").isPositive();
                return id;
            }
        }
    }

    private static void lifecycleAuthRole(Connection connection) throws Exception {
        long tenantId = 999L;
        String roleCode = "H142_LIFECYCLE_ROLE";
        try {
            // First active insert succeeds.
            long firstId = insertAuthRole(connection, tenantId, roleCode, 0);
            connection.commit();
            // Duplicate active insert rejected (must be MySQL 1062/23000).
            assertDuplicateRejected(connection,
                    () -> insertAuthRole(connection, tenantId, roleCode, 0),
                    "duplicate active auth_role insert should be rejected by unique index");
            // First soft delete succeeds.
            softDeleteById(connection, "auth_role", firstId);
            connection.commit();
            // Recreate same key succeeds (first row's generated column is now NULL — NULLs are distinct).
            long secondId = insertAuthRole(connection, tenantId, roleCode, 0);
            connection.commit();
            // Second soft delete succeeds.
            softDeleteById(connection, "auth_role", secondId);
            connection.commit();
        } finally {
            // Cleanup always runs even on assertion failure.
            cleanupLifecycleRows(connection, "auth_role", "tenant_id = " + tenantId
                    + " AND role_code = '" + roleCode + "'");
            connection.commit();
        }
    }

    private static void lifecycleAuthUserRole(Connection connection) throws Exception {
        long tenantId = 999L;
        long userId = 9001L;
        // H142 review: resolve a real role id at runtime; do not assume id=1.
        long roleId = resolvePlatformOperatorRoleId(connection);
        try {
            // First active insert succeeds.
            long firstId = insertAuthUserRole(connection, tenantId, userId, roleId);
            connection.commit();
            // Duplicate active insert rejected.
            assertDuplicateRejected(connection,
                    () -> insertAuthUserRole(connection, tenantId, userId, roleId),
                    "duplicate active auth_user_role insert should be rejected by unique index");
            // First soft delete succeeds.
            softDeleteById(connection, "auth_user_role", firstId);
            connection.commit();
            // Recreate same key succeeds.
            long secondId = insertAuthUserRole(connection, tenantId, userId, roleId);
            connection.commit();
            // Second soft delete succeeds.
            softDeleteById(connection, "auth_user_role", secondId);
            connection.commit();
        } finally {
            cleanupLifecycleRows(connection, "auth_user_role", "tenant_id = " + tenantId
                    + " AND user_id = " + userId);
            connection.commit();
        }
    }

    private static void lifecycleAuthPermission(Connection connection) throws Exception {
        String permissionCode = "h142:lifecycle:permission";
        try {
            // First active insert succeeds.
            long firstId = insertAuthPermission(connection, permissionCode);
            connection.commit();
            // Duplicate active insert rejected.
            assertDuplicateRejected(connection,
                    () -> insertAuthPermission(connection, permissionCode),
                    "duplicate active auth_permission insert should be rejected by unique index");
            // First soft delete succeeds.
            softDeleteById(connection, "auth_permission", firstId);
            connection.commit();
            // Recreate same key succeeds.
            long secondId = insertAuthPermission(connection, permissionCode);
            connection.commit();
            // Second soft delete succeeds.
            softDeleteById(connection, "auth_permission", secondId);
            connection.commit();
        } finally {
            cleanupLifecycleRows(connection, "auth_permission",
                    "permission_code = '" + permissionCode + "'");
            connection.commit();
        }
    }

    private static void lifecycleAuthRolePermission(Connection connection) throws Exception {
        long tenantId = 999L;
        // H142 review: resolve a real role id at runtime; do not assume id=1.
        long roleId = resolvePlatformOperatorRoleId(connection);
        // H142 review: create its own temporary auth_permission row, capture its generated id,
        // and clean up both role_permission and the temporary permission in finally.
        // Do not assume permissionId=1 or depend on lifecycle method order.
        String permissionCode = "h142:role_permission:lifecycle";
        long tempPermissionId = -1L;
        try {
            tempPermissionId = insertAuthPermission(connection, permissionCode);
            connection.commit();
            final long permissionId = tempPermissionId;
            // First active insert succeeds.
            long firstId = insertAuthRolePermission(connection, tenantId, roleId, permissionId);
            connection.commit();
            // Duplicate active insert rejected.
            assertDuplicateRejected(connection,
                    () -> insertAuthRolePermission(connection, tenantId, roleId, permissionId),
                    "duplicate active auth_role_permission insert should be rejected by unique index");
            // First soft delete succeeds.
            softDeleteById(connection, "auth_role_permission", firstId);
            connection.commit();
            // Recreate same key succeeds.
            long secondId = insertAuthRolePermission(connection, tenantId, roleId, permissionId);
            connection.commit();
            // Second soft delete succeeds.
            softDeleteById(connection, "auth_role_permission", secondId);
            connection.commit();
        } finally {
            // Clean role_permission rows first (FK-less, but logical ordering), then the temp permission.
            cleanupLifecycleRows(connection, "auth_role_permission",
                    "tenant_id = " + tenantId + " AND role_id = " + roleId
                            + " AND permission_id = " + tempPermissionId);
            cleanupLifecycleRows(connection, "auth_permission",
                    "permission_code = '" + permissionCode + "'");
            connection.commit();
        }
    }

    private static long insertAuthRole(Connection connection, long tenantId, String roleCode, int isAdmin)
            throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO auth_role (tenant_id, role_code, role_name, is_admin, is_builtin, status, "
                            + "description, creator, create_time, updater, update_time, deleted) "
                            + "VALUES (" + tenantId + ", '" + roleCode + "', '" + roleCode + "', " + isAdmin
                            + ", 0, 'ACTIVE', 'H142 lifecycle', 'h142', NOW(), 'h142', NOW(), 0)",
                    Statement.RETURN_GENERATED_KEYS);
            try (ResultSet rs = statement.getGeneratedKeys()) {
                assertThat(rs.next()).isTrue();
                return rs.getLong(1);
            }
        }
    }

    private static long insertAuthUserRole(Connection connection, long tenantId, long userId, long roleId)
            throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO auth_user_role (tenant_id, user_id, role_id, creator, create_time, "
                            + "updater, update_time, deleted) "
                            + "VALUES (" + tenantId + ", " + userId + ", " + roleId
                            + ", 'h142', NOW(), 'h142', NOW(), 0)",
                    Statement.RETURN_GENERATED_KEYS);
            try (ResultSet rs = statement.getGeneratedKeys()) {
                assertThat(rs.next()).isTrue();
                return rs.getLong(1);
            }
        }
    }

    private static long insertAuthPermission(Connection connection, String permissionCode)
            throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO auth_permission (permission_code, permission_name, subsystem_id, module_name, "
                            + "description, risk_level, creator, create_time, updater, update_time, deleted) "
                            + "VALUES ('" + permissionCode + "', 'H142 lifecycle', NULL, 'h142', "
                            + "'H142 lifecycle', 'LOW', 'h142', NOW(), 'h142', NOW(), 0)",
                    Statement.RETURN_GENERATED_KEYS);
            try (ResultSet rs = statement.getGeneratedKeys()) {
                assertThat(rs.next()).isTrue();
                return rs.getLong(1);
            }
        }
    }

    private static long insertAuthRolePermission(Connection connection, long tenantId, long roleId, long permissionId)
            throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO auth_role_permission (tenant_id, role_id, permission_id, creator, create_time, "
                            + "updater, update_time, deleted) "
                            + "VALUES (" + tenantId + ", " + roleId + ", " + permissionId
                            + ", 'h142', NOW(), 'h142', NOW(), 0)",
                    Statement.RETURN_GENERATED_KEYS);
            try (ResultSet rs = statement.getGeneratedKeys()) {
                assertThat(rs.next()).isTrue();
                return rs.getLong(1);
            }
        }
    }

    private static void softDeleteById(Connection connection, String table, long id) throws Exception {
        try (Statement statement = connection.createStatement()) {
            int rows = statement.executeUpdate(
                    "UPDATE " + table + " SET deleted = 1, update_time = NOW() WHERE id = " + id);
            assertThat(rows).as("soft delete should affect exactly one row in %s", table).isEqualTo(1);
        }
    }

    private static void cleanupLifecycleRows(Connection connection, String table, String whereClause)
            throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "DELETE FROM " + table + " WHERE " + whereClause);
        }
    }
}
