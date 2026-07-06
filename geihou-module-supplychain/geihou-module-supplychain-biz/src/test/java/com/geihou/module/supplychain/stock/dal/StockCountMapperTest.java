package com.geihou.module.supplychain.stock.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockCountRecordDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockCountSessionDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockCountRecordMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockCountSessionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StockCountSessionMapper} and {@link StockCountRecordMapper}.
 *
 * <p>Covers: session insert+selectById, session selectByTenantAndCode, session updateStatus,
 * record insert+selectById, record selectBySession, record selectBySessionAndItem.
 * Verifies that StockCountRecordMapper has NO update/delete methods (INSERT-only).
 *
 * <p>Source: TASK-G2-02I-2 §9.1.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_count_mapper_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockCountMapperTest {

    @Autowired
    private StockCountSessionMapper sessionMapper;
    @Autowired
    private StockCountRecordMapper recordMapper;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void session_insert_andSelectById_success() {
        StockCountSessionDO session = buildSessionDO(1L, "COUNT-1-001", "FULL");
        sessionMapper.insert(session);

        StockCountSessionDO found = sessionMapper.selectByIdAndTenant(session.getId(), 1L);
        assertThat(found).isNotNull();
        assertThat(found.getSessionCode()).isEqualTo("COUNT-1-001");
        assertThat(found.getCountType()).isEqualTo("FULL");
        assertThat(found.getStatus()).isEqualTo("PLANNING");
    }

    @Test
    void session_selectByTenantAndCode_success() {
        StockCountSessionDO session = buildSessionDO(1L, "COUNT-1-002", "CYCLE");
        sessionMapper.insert(session);

        StockCountSessionDO found = sessionMapper.selectByTenantAndCode(1L, "COUNT-1-002");
        assertThat(found).isNotNull();
        assertThat(found.getCountType()).isEqualTo("CYCLE");
    }

    @Test
    void session_selectByTenantAndCode_tenantIsolation_returnsNull() {
        StockCountSessionDO session = buildSessionDO(1L, "COUNT-1-003", "SPOT");
        sessionMapper.insert(session);

        // Switch to tenant 2 and query — should return null
        TenantContextHolder.setTenantId(2L);
        StockCountSessionDO found = sessionMapper.selectByTenantAndCode(2L, "COUNT-1-003");
        assertThat(found).isNull();
    }

    @Test
    void session_updateStatus_success() {
        StockCountSessionDO session = buildSessionDO(1L, "COUNT-1-004", "FULL");
        sessionMapper.insert(session);

        int rows = sessionMapper.updateStartByTenant(
                session.getId(), 1L,
                "IN_PROGRESS",
                LocalDateTime.now(),
                100L,
                "PLANNING",
                "100",
                LocalDateTime.now()
        );
        assertThat(rows).isEqualTo(1);

        StockCountSessionDO updated = sessionMapper.selectByIdAndTenant(session.getId(), 1L);
        assertThat(updated.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(updated.getStartTime()).isNotNull();
    }

    @Test
    void record_insert_andSelectById_success() {
        StockCountSessionDO session = buildSessionDO(1L, "COUNT-1-005", "FULL");
        sessionMapper.insert(session);

        StockCountRecordDO record = buildRecordDO(1L, session.getId(), 1001L,
                new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("-5"));
        recordMapper.insert(record);

        StockCountRecordDO found = recordMapper.selectByIdAndTenant(record.getId(), 1L);
        assertThat(found).isNotNull();
        assertThat(found.getSystemQty()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(found.getActualQty()).isEqualByComparingTo(new BigDecimal("95"));
        assertThat(found.getDiffQty()).isEqualByComparingTo(new BigDecimal("-5"));
        assertThat(found.getAdjustmentEventId()).isNull(); // not backfilled
    }

    @Test
    void record_selectBySession_success() {
        StockCountSessionDO session = buildSessionDO(1L, "COUNT-1-006", "FULL");
        sessionMapper.insert(session);

        recordMapper.insert(buildRecordDO(1L, session.getId(), 1001L,
                new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("-5")));
        recordMapper.insert(buildRecordDO(1L, session.getId(), 1002L,
                new BigDecimal("50"), new BigDecimal("55"), new BigDecimal("5")));
        recordMapper.insert(buildRecordDO(1L, session.getId(), 1003L,
                new BigDecimal("30"), new BigDecimal("30"), new BigDecimal("0")));

        List<StockCountRecordDO> records = recordMapper.selectBySession(session.getId(), 1L);
        assertThat(records).hasSize(3);
    }

    @Test
    void record_selectBySessionAndItem_success() {
        StockCountSessionDO session = buildSessionDO(1L, "COUNT-1-007", "FULL");
        sessionMapper.insert(session);

        recordMapper.insert(buildRecordDO(1L, session.getId(), 1001L,
                new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("-5")));

        StockCountRecordDO found = recordMapper.selectBySessionAndItem(session.getId(), 1001L, 1L);
        assertThat(found).isNotNull();
        assertThat(found.getStockItemId()).isEqualTo(1001L);

        // Non-existent item returns null
        StockCountRecordDO notFound = recordMapper.selectBySessionAndItem(session.getId(), 9999L, 1L);
        assertThat(notFound).isNull();
    }

    @Test
    void record_selectDiffBySession_returnsOnlyDiffRecords() {
        StockCountSessionDO session = buildSessionDO(1L, "COUNT-1-008", "FULL");
        sessionMapper.insert(session);

        recordMapper.insert(buildRecordDO(1L, session.getId(), 1001L,
                new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("-5")));
        recordMapper.insert(buildRecordDO(1L, session.getId(), 1002L,
                new BigDecimal("50"), new BigDecimal("55"), new BigDecimal("5")));
        recordMapper.insert(buildRecordDO(1L, session.getId(), 1003L,
                new BigDecimal("30"), new BigDecimal("30"), new BigDecimal("0")));

        List<StockCountRecordDO> diffRecords = recordMapper.selectDiffBySession(session.getId(), 1L);
        assertThat(diffRecords).hasSize(2); // only diff_qty != 0
    }

    // --- Helpers ---

    private StockCountSessionDO buildSessionDO(Long tenantId, String sessionCode, String countType) {
        StockCountSessionDO session = new StockCountSessionDO();
        session.setTenantId(tenantId);
        session.setSessionCode(sessionCode);
        session.setLocationId(10L);
        session.setCountType(countType);
        session.setStatus("PLANNING");
        session.setOperatorUserId(100L);
        session.setCreator("100");
        session.setCreateTime(LocalDateTime.now());
        session.setUpdater("100");
        session.setUpdateTime(LocalDateTime.now());
        session.setDeleted(false);
        return session;
    }

    private StockCountRecordDO buildRecordDO(Long tenantId, Long sessionId, Long stockItemId,
                                              BigDecimal systemQty, BigDecimal actualQty, BigDecimal diffQty) {
        StockCountRecordDO record = new StockCountRecordDO();
        record.setTenantId(tenantId);
        record.setSessionId(sessionId);
        record.setStockItemId(stockItemId);
        record.setSystemQty(systemQty);
        record.setActualQty(actualQty);
        record.setDiffQty(diffQty);
        if (diffQty.compareTo(BigDecimal.ZERO) != 0) {
            record.setDiffReason("这是一个测试用的差异原因，长度超过三十个字符以满足要求。");
        }
        record.setCreateTime(LocalDateTime.now());
        return record;
    }
}
