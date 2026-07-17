package com.geihou.module.finance.stock.saga;

import com.geihou.module.finance.checkout.CheckoutTestSchemaInitializer;
import com.geihou.module.finance.stock.saga.dal.dataobject.FinanceStockCommandDO;
import com.geihou.module.finance.stock.saga.enums.FinanceStockCommandStatus;
import com.geihou.module.finance.stock.saga.enums.FinanceStockSagaType;
import com.geihou.module.finance.stock.saga.enums.FinanceStockTransportMode;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDL ↔ DO ↔ H2 TestSchema ↔ enum consistency guard for
 * {@code finance_stock_command} (test matrix item 1 and item 19).
 *
 * <p>Verifies:
 * <ol>
 *   <li>The Flyway DDL file {@code V02_065__finance_stock_command.sql} exists.</li>
 *   <li>DDL business columns match DO business fields (both directions).</li>
 *   <li>H2 TestSchema column names match DDL column names (both directions).</li>
 *   <li>All 8 {@link FinanceStockCommandStatus} values round-trip via name().</li>
 *   <li>No {@code FAILED} or {@code RESOLVING} in the status enum.</li>
 *   <li>{@link FinanceStockSagaType} has exactly CHECKOUT and REFUND.</li>
 *   <li>{@link FinanceStockTransportMode} has exactly LOCAL_API_V1 and HMAC_RPC_V1.</li>
 * </ol>
 */
class FinanceStockCommandDdlConsistencyTest {

    private static final Set<String> AUDIT_COLUMNS = Set.of(
            "id", "create_time", "update_time"
    );

    private static final String MIGRATION_DIR =
            "src/main/resources/db/migration";
    private static final String DDL_FILE =
            "V02_065__finance_stock_command.sql";
    private static final String TABLE_NAME =
            "finance_stock_command";

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
    void statusEnum_hasExactly8Values_noFailed() {
        FinanceStockCommandStatus[] values = FinanceStockCommandStatus.values();
        assertThat(values).hasSize(8);

        Set<String> names = new TreeSet<>();
        for (FinanceStockCommandStatus v : values) {
            names.add(v.name());
        }
        assertThat(names).containsExactlyInAnyOrder(
                "PENDING", "IN_FLIGHT", "RETRY_WAIT", "UNKNOWN",
                "SUCCEEDED", "NO_EFFECT", "CANCELLED", "STUCK");

        assertThat(names).doesNotContain("FAILED");
        assertThat(names).doesNotContain("RESOLVING");
    }

    @Test
    void statusEnum_roundTripViaName() {
        for (FinanceStockCommandStatus status : FinanceStockCommandStatus.values()) {
            String name = status.name();
            FinanceStockCommandStatus restored =
                    FinanceStockCommandStatus.valueOf(name);
            assertThat(restored).isSameAs(status);
        }
    }

    @Test
    void statusEnum_terminalDetection() {
        assertThat(FinanceStockCommandStatus.SUCCEEDED.isTerminal()).isTrue();
        assertThat(FinanceStockCommandStatus.NO_EFFECT.isTerminal()).isTrue();
        assertThat(FinanceStockCommandStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(FinanceStockCommandStatus.STUCK.isTerminal()).isTrue();

        assertThat(FinanceStockCommandStatus.PENDING.isTerminal()).isFalse();
        assertThat(FinanceStockCommandStatus.IN_FLIGHT.isTerminal()).isFalse();
        assertThat(FinanceStockCommandStatus.RETRY_WAIT.isTerminal()).isFalse();
        assertThat(FinanceStockCommandStatus.UNKNOWN.isTerminal()).isFalse();
    }

    @Test
    void sagaType_hasExactlyTwoValues() {
        FinanceStockSagaType[] values = FinanceStockSagaType.values();
        assertThat(values).hasSize(2);
        assertThat(values).containsExactlyInAnyOrder(
                FinanceStockSagaType.CHECKOUT, FinanceStockSagaType.REFUND);
    }

    @Test
    void transportMode_hasExactlyTwoValues() {
        FinanceStockTransportMode[] values = FinanceStockTransportMode.values();
        assertThat(values).hasSize(2);
        assertThat(values).containsExactlyInAnyOrder(
                FinanceStockTransportMode.LOCAL_API_V1,
                FinanceStockTransportMode.HMAC_RPC_V1);
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
        Field[] fields = FinanceStockCommandDO.class.getDeclaredFields();
        Set<String> snakeNames = new TreeSet<>();
        for (Field field : fields) {
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

    private DataSource createH2DataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setUrl("jdbc:h2:mem:fsc_ddl_consistency;DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private static String camelToSnake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
