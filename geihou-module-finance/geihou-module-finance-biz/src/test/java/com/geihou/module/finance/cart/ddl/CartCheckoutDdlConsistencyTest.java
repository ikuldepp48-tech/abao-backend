package com.geihou.module.finance.cart.ddl;

import com.geihou.module.finance.cart.CartTestSchemaInitializer;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import com.geihou.module.finance.cart.dal.dataobject.CartEventLogDO;
import com.geihou.module.finance.cart.dal.dataobject.CartItemDO;
import com.geihou.module.finance.checkout.CheckoutTestSchemaInitializer;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutIdempotentDO;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutSessionDO;
import com.geihou.module.finance.stock.plan.dal.dataobject.CheckoutCartItemPlanDO;
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
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
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
 *   <li>The seven Flyway DDL files exist.</li>
 *   <li>DDL business columns match DO business fields (both directions).</li>
 *   <li>TestSchema column names match Flyway DDL column names for all seven tables.</li>
 *   <li>TestSchema indexes match Flyway DDL index definitions (columns + unique
 *       flag) for all seven tables.</li>
 * </ol>
 *
 * <p>Framework/audit columns ({@code id, creator, create_time, updater, update_time, deleted})
 * are excluded from the DO↔DDL comparison since they are inherited or intentionally
 * declared as framework fields, not business fields.
 *
 * <p>H2/MySQL type differences are allowed; column-name drift is not.
 * Index names are allowed to differ between H2 and MySQL (H2 scopes index
 * names per-schema rather than per-table); index (columns, unique) parity
 * is enforced instead.
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
            new DdlMapping("V02_065__finance_stock_command.sql", "finance_stock_command", FinanceStockCommandDO.class),
            new DdlMapping("V02_066__checkout_cart_item_plan.sql", "checkout_cart_item_plan", CheckoutCartItemPlanDO.class)
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

    @Test
    void testSchemaIndexes_matchDdlIndexes() throws Exception {
        DataSource dataSource = createH2DataSource();
        CheckoutTestSchemaInitializer.initialize(dataSource);

        for (DdlMapping mapping : DDL_MAPPINGS) {
            List<IndexDef> ddlIndexes = parseDdlIndexes(mapping.ddlFileName);
            List<IndexDef> h2Indexes = getH2TableIndexes(dataSource, mapping.tableName);

            // Every DDL index must have a matching H2 index (same columns+unique).
            // Name comparison is intentionally skipped - see IndexDef.equals Javadoc.
            List<IndexDef> ddlNotInH2 = ddlIndexes.stream()
                    .filter(idx -> !h2Indexes.contains(idx))
                    .collect(Collectors.toList());
            assertThat(ddlNotInH2)
                    .as("[%s] DDL indexes missing from H2 TestSchema (columns+unique must match)",
                            mapping.tableName)
                    .isEmpty();

            // Every H2 index (except PRIMARY KEY, already filtered) must match
            // a DDL index by columns+unique.
            List<IndexDef> h2NotInDdl = h2Indexes.stream()
                    .filter(idx -> !ddlIndexes.contains(idx))
                    .collect(Collectors.toList());
            assertThat(h2NotInDdl)
                    .as("[%s] H2 TestSchema has extra indexes not declared in DDL",
                            mapping.tableName)
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
     * Parse {@code UNIQUE KEY} and {@code KEY} definitions from a Flyway DDL
     * file.
     *
     * <p>Pattern handles multi-line declarations (name and column list may
     * span lines) and case-insensitively captures:
     * <ul>
     *   <li>whether the index is unique (group 1 = "UNIQUE " or null)</li>
     *   <li>the index name (group 2)</li>
     *   <li>the comma-separated column list (group 3)</li>
     * </ul>
     *
     * <p>Column names are trimmed, lowercased, and kept in declaration order.
     */
    private List<IndexDef> parseDdlIndexes(String ddlFileName) throws IOException {
        Path path = resolveMigrationPath(ddlFileName);
        String content = Files.readString(path, StandardCharsets.UTF_8);

        // (?is) = case-insensitive + dotall (. matches newlines)
        // \b before KEY prevents matching "_key" in column names like
        // "idempotent_key VARCHAR(64)" (which would otherwise parse as
        // an index named "varchar" on column "64").
        Pattern indexPattern = Pattern.compile(
                "(?is)(UNIQUE\\s+)?\\bKEY\\s+(\\w+)\\s*\\(\\s*([^)]+?)\\s*\\)");

        List<IndexDef> indexes = new ArrayList<>();
        Matcher m = indexPattern.matcher(content);
        while (m.find()) {
            boolean unique = m.group(1) != null;
            String name = m.group(2).toLowerCase();
            List<String> columns = Arrays.stream(m.group(3).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
            assertThat(columns)
                    .as("Index %s in %s has no columns", name, ddlFileName)
                    .isNotEmpty();
            indexes.add(new IndexDef(name, columns, unique));
        }
        assertThat(indexes)
                .as("No indexes parsed from %s", ddlFileName)
                .isNotEmpty();
        return indexes;
    }

    /**
     * Query H2 for index definitions of a table via JDBC
     * {@link DatabaseMetaData#getIndexInfo}.
     *
     * <p>Excludes the PRIMARY KEY index by name. For each remaining index,
     * captures the name, ordered column list (by {@code ORDINAL_POSITION}),
     * and uniqueness flag (from {@code NON_UNIQUE}).
     */
    private List<IndexDef> getH2TableIndexes(DataSource dataSource, String tableName)
            throws SQLException {
        Map<String, TreeMap<Short, String>> colsByName = new LinkedHashMap<>();
        Map<String, Boolean> uniqueByName = new HashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            // getIndexInfo(catalog, schema, table, approximate, unique)
            // H2 schema for in-memory DBs is "PUBLIC"; table names are upper-cased.
            try (ResultSet rs = md.getIndexInfo(null, "PUBLIC",
                    tableName.toUpperCase(), false, false)) {
                while (rs.next()) {
                    String idxName = rs.getString("INDEX_NAME");
                    if (idxName == null) {
                        continue;
                    }
                    // H2 names primary-key indexes "PRIMARY_KEY" or
                    // "PRIMARY_KEY_<n>" (counter suffix). Filter by prefix.
                    String idxNameUpper = idxName.toUpperCase();
                    if (idxNameUpper.equals("PRIMARY_KEY")
                            || idxNameUpper.startsWith("PRIMARY_KEY_")) {
                        continue;
                    }
                    String colName = rs.getString("COLUMN_NAME");
                    short ordinal = rs.getShort("ORDINAL_POSITION");
                    boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                    String idxNameLower = idxName.toLowerCase();
                    colsByName.computeIfAbsent(idxNameLower, k -> new TreeMap<>())
                            .put(ordinal, colName.toLowerCase());
                    uniqueByName.merge(idxNameLower, !nonUnique, (a, b) -> a || b);
                }
            }
        }

        List<IndexDef> result = new ArrayList<>();
        for (Map.Entry<String, TreeMap<Short, String>> e : colsByName.entrySet()) {
            List<String> cols = new ArrayList<>(e.getValue().values());
            result.add(new IndexDef(e.getKey(), cols,
                    uniqueByName.getOrDefault(e.getKey(), false)));
        }
        assertThat(result)
                .as("No non-PK indexes found in H2 for table %s", tableName)
                .isNotEmpty();
        return result;
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

    /**
     * Index definition: name, ordered columns, unique flag.
     *
     * <p>Equality compares <strong>columns (ordered, case-insensitive) and
     * unique flag only</strong>, not the name. H2 scopes index names to the
     * schema (not per-table like MySQL), so two DDL indexes that legitimately
     * share a name across tables (e.g. {@code idx_expire_time} on
     * {@code checkout_session} and {@code checkout_idempotent}) cannot both
     * exist in H2 with the same name. Comparing by (columns, unique) keeps
     * the test strict on coverage while tolerating H2's naming limitation.
     *
     * <p>The name is retained in {@link #toString()} for debuggability.
     */
    private record IndexDef(String name, List<String> columns, boolean unique) {
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof IndexDef that)) {
                return false;
            }
            return columns.equals(that.columns)
                    && unique == that.unique;
        }

        @Override
        public int hashCode() {
            return Objects.hash(columns, unique);
        }

        @Override
        public String toString() {
            return (unique ? "UNIQUE " : "") + name + columns;
        }
    }
}
