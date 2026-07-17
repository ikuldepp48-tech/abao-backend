package com.geihou.module.finance.cart.ddl;

import com.geihou.module.finance.cart.CartTestSchemaInitializer;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import com.geihou.module.finance.cart.dal.dataobject.CartEventLogDO;
import com.geihou.module.finance.cart.dal.dataobject.CartItemDO;
import com.geihou.module.finance.checkout.CheckoutTestSchemaInitializer;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutIdempotentDO;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutSessionDO;
import com.geihou.module.finance.stock.saga.dal.dataobject.FinanceStockCommandDO;
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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDL ↔ DO ↔ TestSchema consistency guard for cart/checkout tables.
 *
 * <p>This test ensures that:
 * <ol>
 *   <li>The six Flyway DDL files exist.</li>
 *   <li>DDL business columns match DO business fields (both directions).</li>
 *   <li>TestSchema column names match Flyway DDL column names for all six tables.</li>
 * </ol>
 *
 * <p>Framework/audit columns ({@code id, creator, create_time, updater, update_time, deleted})
 * are excluded from the DO↔DDL comparison since they are inherited or intentionally
 * declared as framework fields, not business fields.
 *
 * <p>H2/MySQL type differences are allowed; column-name drift is not.
 */
class CartCheckoutDdlConsistencyTest {

    // ── Audit / framework columns excluded from DO ↔ DDL comparison ──────────
    private static final Set<String> AUDIT_COLUMNS = Set.of(
            "id", "creator", "create_time", "updater", "update_time", "deleted"
    );

    // ── DDL files to verify ──────────────────────────────────────────────────
    private static final String MIGRATION_DIR =
            "src/main/resources/db/migration";

    private static final List<DdlMapping> DDL_MAPPINGS = List.of(
            new DdlMapping("V02_060__cart.sql", "cart", CartDO.class),
            new DdlMapping("V02_061__cart_item.sql", "cart_item", CartItemDO.class),
            new DdlMapping("V02_062__cart_event_log.sql", "cart_event_log", CartEventLogDO.class),
            new DdlMapping("V02_063__checkout_session.sql", "checkout_session", CheckoutSessionDO.class),
            new DdlMapping("V02_064__checkout_idempotent.sql", "checkout_idempotent", CheckoutIdempotentDO.class),
            new DdlMapping("V02_065__finance_stock_command.sql", "finance_stock_command", FinanceStockCommandDO.class)
    );

    // ── Keywords that start constraint/index lines, not column definitions ──
    private static final Set<String> CONSTRAINT_KEYWORDS = Set.of(
            "UNIQUE", "KEY", "PRIMARY", "CONSTRAINT", "INDEX", "FOREIGN",
            "FULLTEXT", "SPATIAL", "CHECK"
    );

    // ========================================================================
    // Tests
    // ========================================================================

    @Test
    void ddlFiles_exist() {
        for (DdlMapping mapping : DDL_MAPPINGS) {
            Path path = resolveMigrationPath(mapping.ddlFileName);
            assertThat(Files.exists(path))
                    .as("DDL file must exist: %s", path)
                    .isTrue();
        }
    }

    @Test
    void ddlColumns_matchDoFields() {
        for (DdlMapping mapping : DDL_MAPPINGS) {
            Set<String> ddlColumns = parseDdlColumns(mapping.ddlFileName);
            Set<String> ddlBusinessColumns = new TreeSet<>(ddlColumns);
            ddlBusinessColumns.removeAll(AUDIT_COLUMNS);

            Set<String> doBusinessFields = getDoBusinessFields(mapping.doClass);

            // Every DO business field must have a DDL column
            Set<String> doNotInDdl = new TreeSet<>(doBusinessFields);
            doNotInDdl.removeAll(ddlBusinessColumns);
            assertThat(doNotInDdl)
                    .as("[%s] DO fields missing from DDL", mapping.tableName)
                    .isEmpty();

            // Every DDL business column must have a DO field
            Set<String> ddlNotInDo = new TreeSet<>(ddlBusinessColumns);
            ddlNotInDo.removeAll(doBusinessFields);
            assertThat(ddlNotInDo)
                    .as("[%s] DDL columns missing from DO", mapping.tableName)
                    .isEmpty();
        }
    }

    @Test
    void testSchemaColumns_matchDdlColumns() throws Exception {
        DataSource dataSource = createH2DataSource();
        CheckoutTestSchemaInitializer.initialize(dataSource);

        for (DdlMapping mapping : DDL_MAPPINGS) {
            Set<String> ddlColumns = parseDdlColumns(mapping.ddlFileName);
            Set<String> schemaColumns = getH2TableColumns(dataSource, mapping.tableName);

            // DDL columns not in TestSchema
            Set<String> ddlNotInSchema = new TreeSet<>(ddlColumns);
            ddlNotInSchema.removeAll(schemaColumns);
            assertThat(ddlNotInSchema)
                    .as("[%s] DDL columns missing from TestSchema", mapping.tableName)
                    .isEmpty();

            // TestSchema columns not in DDL
            Set<String> schemaNotInDdl = new TreeSet<>(schemaColumns);
            schemaNotInDdl.removeAll(ddlColumns);
            assertThat(schemaNotInDdl)
                    .as("[%s] TestSchema columns missing from DDL", mapping.tableName)
                    .isEmpty();
        }
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    /**
     * Parse column names from a Flyway DDL file.
     *
     * <p>Extracts the first identifier token from each line inside the
     * {@code CREATE TABLE ( ... )} block, skipping comment lines, constraint
     * lines ({@code UNIQUE KEY}, {@code KEY}, etc.), and the closing
     * {@code ) ENGINE=...} line.
     */
    private Set<String> parseDdlColumns(String ddlFileName) {
        Path path = resolveMigrationPath(ddlFileName);
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read DDL file: " + path, e);
        }

        // Extract content between the outer parentheses of CREATE TABLE
        int createIdx = content.indexOf("CREATE TABLE");
        assertThat(createIdx).as("CREATE TABLE not found in %s", ddlFileName).isGreaterThan(-1);
        int openParen = content.indexOf('(', createIdx);
        int closeParen = content.lastIndexOf(')');
        assertThat(openParen).as("Opening paren not found in %s", ddlFileName).isGreaterThan(-1);
        assertThat(closeParen).as("Closing paren not found in %s", ddlFileName).isGreaterThan(openParen);

        String tableBody = content.substring(openParen + 1, closeParen);
        Set<String> columns = new LinkedHashSet<>();

        // Match column-definition lines: indented identifier followed by type
        // Column name pattern: word characters (letters, digits, underscore)
        Pattern columnPattern = Pattern.compile("^\\s+(\\w+)\\s+\\w+", Pattern.MULTILINE);

        for (String line : tableBody.split("\\n")) {
            String trimmed = line.trim();

            // Skip empty lines
            if (trimmed.isEmpty()) {
                continue;
            }
            // Skip comment lines
            if (trimmed.startsWith("--")) {
                continue;
            }
            // Skip constraint/index lines
            String firstWord = trimmed.split("\\s+")[0];
            if (CONSTRAINT_KEYWORDS.contains(firstWord.toUpperCase())) {
                continue;
            }

            Matcher matcher = columnPattern.matcher(line);
            if (matcher.find()) {
                String columnName = matcher.group(1).toLowerCase();
                columns.add(columnName);
            }
        }

        assertThat(columns)
                .as("No columns parsed from %s", ddlFileName)
                .isNotEmpty();

        return columns;
    }

    /**
     * Get business field names from a DO class via reflection.
     *
     * <p>Converts camelCase field names to snake_case and excludes
     * framework/audit fields.
     */
    private Set<String> getDoBusinessFields(Class<?> doClass) {
        Field[] fields = doClass.getDeclaredFields();
        Set<String> snakeNames = new TreeSet<>();
        for (Field field : fields) {
            String fieldName = field.getName();
            String snakeName = camelToSnake(fieldName);
            if (!AUDIT_COLUMNS.contains(snakeName)) {
                snakeNames.add(snakeName);
            }
        }
        assertThat(snakeNames)
                .as("No business fields found in %s", doClass.getSimpleName())
                .isNotEmpty();
        return snakeNames;
    }

    /**
     * Query H2 INFORMATION_SCHEMA for column names of a table.
     */
    private Set<String> getH2TableColumns(DataSource dataSource, String tableName) throws SQLException {
        Set<String> columns = new TreeSet<>();
        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                     "WHERE TABLE_NAME = UPPER('" + tableName + "') " +
                     "ORDER BY ORDINAL_POSITION";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }
        assertThat(columns)
                .as("No columns found in H2 for table %s", tableName)
                .isNotEmpty();
        return columns;
    }

    /**
     * Create an in-memory H2 DataSource in MySQL compatibility mode.
     */
    private DataSource createH2DataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setUrl("jdbc:h2:mem:ddl_consistency_test;DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    /**
     * Resolve a DDL file name to a Path relative to the module root.
     */
    private Path resolveMigrationPath(String ddlFileName) {
        return Paths.get(MIGRATION_DIR, ddlFileName);
    }

    /**
     * Convert camelCase to snake_case.
     *
     * <p>e.g. {@code tenantId} → {@code tenant_id},
     * {@code skuNameSnapshot} → {@code sku_name_snapshot}.
     */
    private static String camelToSnake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    // ── Data class for DDL → DO mapping ──────────────────────────────────────
    private record DdlMapping(String ddlFileName, String tableName, Class<?> doClass) {}
}
