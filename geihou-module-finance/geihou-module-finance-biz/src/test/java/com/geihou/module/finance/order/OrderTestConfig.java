package com.geihou.module.finance.order;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.framework.tenant.config.TenantMyBatisAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.geihou.module.finance.api.product.ProductApi;
import com.geihou.module.finance.api.product.dto.SkuRespDTO;
import com.geihou.module.finance.api.product.dto.SpuRespDTO;
import com.geihou.module.finance.api.product.dto.AddonGroupRespDTO;
import com.geihou.module.finance.api.product.dto.ComboItemRespDTO;
import com.geihou.module.finance.api.product.dto.SkuAvailabilityRespDTO;
import com.geihou.module.finance.stock.StockTestConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared Spring Boot test configuration for order module integration tests.
 *
 * <p>Imports MyBatis-Plus and tenant isolation auto-configurations.
 * Scans order and stock mappers and services for dependency injection.
 * Provides a mock ProductApi for SKU/SPU snapshot retrieval.
 * Imports StockTestConfig for mock StockEventApi + StockQueryApi.
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = {"com.geihou.module.finance.order", "com.geihou.module.finance.stock"})
@Import({GeihouMyBatisAutoConfiguration.class, TenantMyBatisAutoConfiguration.class, StockTestConfig.class})
@MapperScan({
    "com.geihou.module.finance.order.dal.mapper",
    "com.geihou.module.finance.cart.dal.mapper",
    "com.geihou.module.finance.checkout.dal.mapper"
})
public class OrderTestConfig {

    /**
     * Mock ProductApi for testing order creation with SKU snapshots.
     * Returns predefined SKU/SPU data without needing real product tables.
     */
    @Bean
    public ProductApi productApi() {
        return new MockProductApi();
    }

    public static class MockProductApi implements ProductApi {

        private final Map<Long, SkuRespDTO> skuStore = new HashMap<>();
        private final Map<Long, SpuRespDTO> spuStore = new HashMap<>();

        public MockProductApi() {
            // Pre-seed test data
            SpuRespDTO spu1 = new SpuRespDTO();
            spu1.setId(101L);
            spu1.setSpuCode("BURGER");
            spu1.setSpuName("Burger");
            spu1.setCategoryId(10L);
            spu1.setStatus("ACTIVE");
            spuStore.put(101L, spu1);

            SpuRespDTO spu2 = new SpuRespDTO();
            spu2.setId(102L);
            spu2.setSpuCode("DRINK");
            spu2.setSpuName("Drink");
            spu2.setCategoryId(20L);
            spu2.setStatus("ACTIVE");
            spuStore.put(102L, spu2);

            SkuRespDTO sku1 = new SkuRespDTO();
            sku1.setId(1001L);
            sku1.setSpuId(101L);
            sku1.setSkuCode("SKU_BEEF");
            sku1.setSkuName("Beef Burger");
            sku1.setSellingPrice(new java.math.BigDecimal("12.00"));
            sku1.setStatus("ACTIVE");
            skuStore.put(1001L, sku1);

            SkuRespDTO sku2 = new SkuRespDTO();
            sku2.setId(1002L);
            sku2.setSpuId(101L);
            sku2.setSkuCode("SKU_CHICKEN");
            sku2.setSkuName("Chicken Burger");
            sku2.setSellingPrice(new java.math.BigDecimal("10.00"));
            sku2.setStatus("ACTIVE");
            skuStore.put(1002L, sku2);

            SkuRespDTO sku3 = new SkuRespDTO();
            sku3.setId(2001L);
            sku3.setSpuId(102L);
            sku3.setSkuCode("SKU_COLA");
            sku3.setSkuName("Cola");
            sku3.setSellingPrice(new java.math.BigDecimal("5.00"));
            sku3.setStatus("ACTIVE");
            skuStore.put(2001L, sku3);

            // Non-sellable SKU
            SkuRespDTO sku4 = new SkuRespDTO();
            sku4.setId(3001L);
            sku4.setSpuId(101L);
            sku4.setSkuCode("SKU_PAUSED");
            sku4.setSkuName("Paused Item");
            sku4.setSellingPrice(new java.math.BigDecimal("8.00"));
            sku4.setStatus("PAUSED");
            skuStore.put(3001L, sku4);
        }

        @Override
        public SpuRespDTO getSpu(Long spuId) {
            return spuStore.get(spuId);
        }

        @Override
        public SkuRespDTO getSku(Long skuId) {
            return skuStore.get(skuId);
        }

        @Override
        public Map<Long, SkuRespDTO> batchGetSkus(List<Long> skuIds) {
            Map<Long, SkuRespDTO> result = new HashMap<>();
            for (Long id : skuIds) {
                SkuRespDTO sku = skuStore.get(id);
                if (sku != null) {
                    result.put(id, sku);
                }
            }
            return result;
        }

        @Override
        public SkuAvailabilityRespDTO checkAvailability(Long skuId, Integer quantity) {
            return null; // Not used in order tests
        }

        @Override
        public List<ComboItemRespDTO> expandCombo(Long comboSkuId) {
            return new ArrayList<>(); // Not used in order tests
        }

        @Override
        public List<AddonGroupRespDTO> getAddonGroupsBySpu(Long spuId) {
            return new ArrayList<>(); // Not used in order tests
        }
    }
}
