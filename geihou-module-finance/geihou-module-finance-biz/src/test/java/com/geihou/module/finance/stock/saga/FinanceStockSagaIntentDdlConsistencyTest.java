package com.geihou.module.finance.stock.saga;

import com.geihou.module.finance.api.cart.enums.CartEventTypeEnum;
import com.geihou.module.finance.checkout.CheckoutTestSchemaInitializer;
import com.geihou.module.finance.checkout.framework.CheckoutStatusEnum;
import com.geihou.module.finance.stock.saga.dal.dataobject.FinanceStockSagaIntentDO;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDL ↔ DO ↔ H2 TestSchema ↔ enum consistency guard for
 * {@code finance_stock_saga_intent} (G0-04H185 FIN-CONSISTENCY slice 2C-2D).
 *
 * <p>Verifies:
 * <ol>
 *   <li>The Flyway DDL file {@code V02_068__finance_stock_saga_intent.sql}
 *       exists.</li>
 *   <li>DDL business columns match DO business fields (both directions).</li>
 *   <li>H2 TestSchema column names match DDL column names (both directions).</li>
 *   <li>{@link CheckoutStatusEnum} has exactly 5 values that round-trip via
 *       getCode()/fromCode().</li>
 *   <li>{@link CartEventTypeEnum} has exactly 7 values that round-trip via
 *       getCode()/fromCode().</li>
 *   <li>The DO owns the two frozen finalization-state constants.</li>
 * </ol>
 */
class FinanceStockSagaIntentDdlConsistencyTest {

    private static final Set<String> AUDIT_COLUMNS = Set.of(
            "id", "create_time", "update_time"
    );

    private static final String MIGRATION_DIR =
            "src/main/resources/db/migration";
    private static final String DDL_FILE =
            "V02_068__finance_stock_saga_intent.sql";
    private static final String TABLE_NAME =
            "finance_stock_saga_intent";

    private static final Set<String> CONSTRAINT_KEYWORDS = Set.of(
            "UNIQUE", "KEY", "PRIMARY", "CONSTRAINT", "INDEX", "FOREIGN",
            "FULLTEXT", "SPATIAL", "CHECK"
    );

    @Test
    void ddlFile_exists() {
        Path path = resolveMigrationPath();
        assertThat(Files.exists(path))
                .as("DDL file must exist: %s", path)
                .isTrue();
    }

    @Test
    void ddlColumns_matchDoFields() throws IOException {
        Set<String> ddlColumns = parseDdlColumns();
        Set<String> ddlBusiness = new TreeSet<>(ddlColumns);
        ddlBusiness.removeAll(AUDIT_COLUMNS);

        Set<String> doBusiness = getDoBusinessFields();

        Set<String> doNotInDdl = new TreeSet<>(doBusiness);
        doNotInDdl.removeAll(ddlBusiness);
        assertThat(doNotInDdl)
                .as("DO fields missing from DDL")
                .isEmpty();

        Set<String> ddlNotInDo = new TreeSet<>(ddlBusiness);
        ddlNotInDo.removeAll(doBusiness);
        assertThat(ddlNotInDo)
                .as("DDL columns missing from DO")
                .isEmpty();
    }

    @Test
    void testSchemaColumns_matchDdlColumns() throws Exception {
        Set<String> ddlColumns = parseDdlColumns();
        DataSource dataSource = createH2DataSource();
        CheckoutTestSchemaInitializer.initialize(dataSource);
        Set<String> schemaColumns = getH2TableColumns(dataSource);

        Set<String> ddlNotInSchema = new TreeSet<>(ddlColumns);
        ddlNotInSchema.removeAll(schemaColumns);
        assertThat(ddlNotInSchema)
                .as("DDL columns missing from TestSchema")
                .isEmpty();

        Set<String> schemaNotInDdl = new TreeSet<>(schemaColumns);
        schemaNotInDdl.removeAll(ddlColumns);
        assertThat(schemaNotInDdl)
                .as("TestSchema columns missing from DDL")
                .isEmpty();
    }

    @Test
    void ddl_freezesIndexesWidthsAndMillisecondTimestamps() throws IOException {
        String ddl = Files.readString(resolveMigrationPath(), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");

        assertThat(ddl).containsPattern(
                "finalization_status\\s+VARCHAR\\(32\\)\\s+NOT NULL\\s+DEFAULT 'PENDING'");
        assertThat(ddl).containsPattern("finalized_at\\s+DATETIME\\(3\\)\\s+NULL");
        assertThat(ddl).containsPattern("create_time\\s+DATETIME\\(3\\)\\s+NOT NULL");
        assertThat(ddl).containsPattern("update_time\\s+DATETIME\\(3\\)\\s+NOT NULL");
        assertThat(ddl).containsPattern(
                "UNIQUE KEY uk_fsi_identity \\(tenant_id, saga_type, saga_id\\)");
        assertThat(ddl).containsPattern(
                "KEY idx_fsi_scan \\(tenant_id, finalization_status, update_time\\)");

        assertThat(parseDdlColumns())
                .doesNotContain("deleted", "creator", "updater");
    }

    @Test
    void testSchema_replaysIndexesAndTimestampPrecision() throws Exception {
        DataSource dataSource = createH2DataSource();
        CheckoutTestSchemaInitializer.initialize(dataSource);

        assertThat(getH2UniqueConstraints(dataSource)).contains("UK_FSI_IDENTITY");
        assertThat(getH2Indexes(dataSource).get("IDX_FSI_SCAN"))
                .containsExactly("TENANT_ID", "FINALIZATION_STATUS", "UPDATE_TIME");
        assertThat(getH2DateTimePrecision(dataSource))
                .containsEntry("FINALIZED_AT", 3)
                .containsEntry("CREATE_TIME", 3)
                .containsEntry("UPDATE_TIME", 3);
    }

    @Test
    void checkoutStatusEnum_hasExactly5Values_roundTrips() {
        CheckoutStatusEnum[] values = CheckoutStatusEnum.values();
        assertThat(values).hasSize(5);

        Set<String> codes = new TreeSet<>();
        for (CheckoutStatusEnum v : values) {
            codes.add(v.getCode());
            assertThat(CheckoutStatusEnum.fromCode(v.getCode())).isSameAs(v);
        }
        assertThat(codes).containsExactlyInAnyOrder(
                "INITIATED", "PAID", "ABANDONED", "EXPIRED", "FAILED");
    }

    @Test
    void cartEventTypeEnum_hasExactly7Values_roundTrips() {
        CartEventTypeEnum[] values = CartEventTypeEnum.values();
        assertThat(values).hasSize(7);

        Set<String> codes = new TreeSet<>();
        for (CartEventTypeEnum v : values) {
            codes.add(v.getCode());
            assertThat(CartEventTypeEnum.fromCode(v.getCode())).isSameAs(v);
        }
        assertThat(codes).containsExactlyInAnyOrder(
                "ITEM_ADDED", "ITEM_QUANTITY_CHANGED", "ITEM_REMOVED",
                "CART_CLEARED", "CHECKOUT_STARTED", "CHECKOUT_ABANDONED",
                "CART_EXPIRED");
    }

    @Test
    void intentDo_ownsFrozenStatusConstants() {
        assertThat(FinanceStockSagaIntentDO.FINALIZATION_STATUS_PENDING).isEqualTo("PENDING");
        assertThat(FinanceStockSagaIntentDO.FINALIZATION_STATUS_FINALIZED).isEqualTo("FINALIZED");
        assertThat(FinanceStockSagaIntentStore.FINALIZATION_STATUS_PENDING)
                .isEqualTo(FinanceStockSagaIntentDO.FINALIZATION_STATUS_PENDING);
        assertThat(FinanceStockSagaIntentStore.FINALIZATION_STATUS_FINALIZED)
                .isEqualTo(FinanceStockSagaIntentDO.FINALIZATION_STATUS_FINALIZED);
    }

    // ==================== Helpers ====================

    private Path resolveMigrationPath() {
        return Paths.get(MIGRATION_DIR, DDL_FILE);
    }

    private Set<String> parseDdlColumns() throws IOException {
        Path path = resolveMigrationPath();
        String content = Files.readString(path, StandardCharsets.UTF_8);

        int createIdx = content.indexOf("CREATE TABLE");
        assertThat(createIdx).as("CREATE TABLE not found").isGreaterThan(-1);
        int openParen = content.indexOf('(', createIdx);
        int closeParen = content.lastIndexOf(')');
        String tableBody = content.substring(openParen + 1, closeParen);

        Set<String> columns = new TreeSet<>();
        Pattern columnPattern = Pattern.compile("^\\s+(\\w+)\\s+\\w+", Pattern.MULTILINE);

        for (String line : tableBody.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            String firstWord = trimmed.split("\\s+")[0];
            if (CONSTRAINT_KEYWORDS.contains(firstWord.toUpperCase())) {
                continue;
            }
            Matcher matcher = columnPattern.matcher(line);
            if (matcher.find()) {
                columns.add(matcher.group(1).toLowerCase());
            }
        }
        assertThat(columns).as("No columns parsed").isNotEmpty();
        return columns;
    }

    private Set<String> getDoBusinessFields() {
        Field[] fields = FinanceStockSagaIntentDO.class.getDeclaredFields();
        Set<String> snakeNames = new TreeSet<>();
        for (Field field : fields) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String snake = camelToSnake(field.getName());
            if (!AUDIT_COLUMNS.contains(snake)) {
                snakeNames.add(snake);
            }
        }
        assertThat(snakeNames).as("No business fields found").isNotEmpty();
        return snakeNames;
    }

    private Set<String> getH2TableColumns(DataSource dataSource) throws SQLException {
        Set<String> columns = new TreeSet<>();
        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                     "WHERE TABLE_NAME = UPPER('" + TABLE_NAME + "') " +
                     "ORDER BY ORDINAL_POSITION";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }
        assertThat(columns).as("No columns found in H2").isNotEmpty();
        return columns;
    }

    private Set<String> getH2UniqueConstraints(DataSource dataSource) throws SQLException {
        Set<String> constraints = new TreeSet<>();
        String sql = "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                + "WHERE TABLE_NAME = UPPER('" + TABLE_NAME + "') "
                + "AND CONSTRAINT_TYPE = 'UNIQUE'";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                constraints.add(rs.getString("CONSTRAINT_NAME"));
            }
        }
        return constraints;
    }

    private Map<String, List<String>> getH2Indexes(DataSource dataSource) throws SQLException {
        Map<String, List<String>> indexes = new TreeMap<>();
        String sql = "SELECT INDEX_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.INDEX_COLUMNS "
                + "WHERE TABLE_NAME = UPPER('" + TABLE_NAME + "') "
                + "ORDER BY INDEX_NAME, ORDINAL_POSITION";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                indexes.computeIfAbsent(rs.getString("INDEX_NAME"), ignored -> new ArrayList<>())
                        .add(rs.getString("COLUMN_NAME"));
            }
        }
        return indexes;
    }

    private Map<String, Integer> getH2DateTimePrecision(DataSource dataSource) throws SQLException {
        Map<String, Integer> precision = new TreeMap<>();
        String sql = "SELECT COLUMN_NAME, DATETIME_PRECISION FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = UPPER('" + TABLE_NAME + "') "
                + "AND COLUMN_NAME IN ('FINALIZED_AT', 'CREATE_TIME', 'UPDATE_TIME')";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                precision.put(rs.getString("COLUMN_NAME"), rs.getInt("DATETIME_PRECISION"));
            }
        }
        return precision;
    }

    private DataSource createH2DataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setUrl("jdbc:h2:mem:fsi_ddl_consistency;DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private static String camelToSnake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
