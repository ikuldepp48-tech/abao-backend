package com.geihou.module.supplychain.stock.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseItemRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreItemRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageDecisionRespDTO;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageModeEnum;
import com.geihou.module.supplychain.api.stock.enums.SupplychainCommandOperationEnum;
import com.geihou.module.supplychain.stock.dal.dataobject.SupplychainCommandJournalDO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Shared test support utilities for {@link CommandCodecImpl} and
 * {@link CommandExecutorImpl} tests.
 *
 * <p>Provides fixed constants, test clock/sleeper implementations, sample
 * business result DTOs for all 6 operations, a ready-to-use
 * {@link CommandCodecImpl}, and a journal DO factory.
 */
public final class TestSupport {

    public static final Long TENANT_ID = 1001L;
    public static final String BUSINESS_COMMAND_ID = "cmd-test-001";
    public static final String SHA256_HEX =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    public static final LocalDateTime FIXED_TIME =
            LocalDateTime.of(2026, 7, 15, 10, 0, 0);

    private TestSupport() {
    }

    // --- Test implementations ---

    public static CommandClock fixedClock() {
        return () -> FIXED_TIME;
    }

    public static CommandSleeper noOpSleeper() {
        return ms -> { };
    }

    public static CommandRetryConfig testRetryConfig() {
        CommandRetryConfig config = new CommandRetryConfig();
        config.validate();
        return config;
    }

    public static CommandCodec commandCodec() {
        return new CommandCodecImpl(new ObjectMapper(), fixedClock());
    }

    // --- Business result factories ---

    public static Long sampleReserveResult() {
        return 123L;
    }

    public static Long sampleCommitResult() {
        return 456L;
    }

    public static SalesOutBomReverseRespDTO sampleSalesOutBomReverse() {
        SalesOutBomReverseRespDTO dto = new SalesOutBomReverseRespDTO();
        dto.setTenantId(TENANT_ID);
        dto.setProductId(100L);
        dto.setSkuCode("SKU-FINISHED");
        dto.setQuantity(new BigDecimal("5.00"));
        dto.setRecipeId(200L);
        dto.setRecipeVersion(1);
        SalesOutBomReverseItemRespDTO item = new SalesOutBomReverseItemRespDTO();
        item.setComponentProductId(101L);
        item.setSkuCode("SKU-RAW");
        item.setUnit("KG");
        item.setStockItemId(1001L);
        item.setQuantity(new BigDecimal("2.00"));
        item.setEventId(5001L);
        item.setClientRequestId("cr-001::101");
        item.setRecipeId(200L);
        item.setRecipeVersion(1);
        dto.setItems(List.of(item));
        return dto;
    }

    public static SalesReverseRestoreRespDTO sampleSalesReverseRestore() {
        SalesReverseRestoreRespDTO dto = new SalesReverseRestoreRespDTO();
        dto.setRestoredItemCount(1);
        SalesReverseRestoreItemRespDTO item = new SalesReverseRestoreItemRespDTO();
        item.setOriginalEventId(5001L);
        item.setRestoreEventId(6001L);
        item.setStockItemId(1001L);
        item.setLocationId(2001L);
        item.setQuantity(new BigDecimal("2.00"));
        item.setUnit("KG");
        item.setRecipeId(200L);
        item.setRecipeVersion(1);
        dto.setItems(List.of(item));
        return dto;
    }

    public static StockCoverageDecisionRespDTO sampleObserveAuditOnly() {
        return new StockCoverageDecisionRespDTO(StockCoverageModeEnum.AUDIT_ONLY, false);
    }

    public static StockCoverageDecisionRespDTO sampleObserveEnforce() {
        return new StockCoverageDecisionRespDTO(StockCoverageModeEnum.ENFORCE, true);
    }

    // --- Journal DO factory ---

    public static SupplychainCommandJournalDO journalDO(
            SupplychainCommandOperationEnum operation,
            String businessCommandId,
            String requestBodySha256,
            String resultSnapshot) {
        SupplychainCommandJournalDO journal = new SupplychainCommandJournalDO();
        journal.setId(1L);
        journal.setTenantId(TENANT_ID);
        journal.setOperation(operation.getCode());
        journal.setBusinessCommandId(businessCommandId);
        journal.setRequestBodySha256(requestBodySha256);
        journal.setResultSchemaVersion(1);
        journal.setResultSnapshot(resultSnapshot);
        journal.setExecutedAt(FIXED_TIME);
        journal.setCreateTime(FIXED_TIME);
        return journal;
    }
}
