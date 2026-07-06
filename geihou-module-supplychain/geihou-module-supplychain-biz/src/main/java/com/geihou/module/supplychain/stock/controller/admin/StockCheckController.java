package com.geihou.module.supplychain.stock.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.supplychain.api.stock.dto.SkuQuantityDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCheckRespDTO;
import com.geihou.module.supplychain.stock.check.StockCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin controller for read-only stock sufficiency preflight check (G2-02C).
 *
 * <p><b>GET-only</b>: this controller exposes a single GET endpoint for the
 * read-only BOM-aware stock check. No write operations.
 *
 * <p>Source: TASK-G2-02C.
 */
@RestController
@RequestMapping("/admin/stock/check")
public class StockCheckController {

    @Autowired
    private StockCheckService stockCheckService;

    /**
     * Read-only stock sufficiency preflight check.
     *
     * <p>Accepts comma-separated skuCodes and quantities as query parameters.
     * Example: {@code GET /admin/stock/check?tenantId=1&skuCodes=A,B&quantities=2,3}
     *
     * @param tenantId  tenant ID (required)
     * @param skuCodes  comma-separated SKU codes (required)
     * @param quantities comma-separated quantities (optional; defaults to 1 each)
     * @return stock check result with per-component sufficiency status
     */
    @GetMapping
    public CommonResult<StockCheckRespDTO> checkStock(
            @RequestParam Long tenantId,
            @RequestParam String skuCodes,
            @RequestParam(required = false) String quantities) {

        List<SkuQuantityDTO> items = parseItems(skuCodes, quantities);
        StockCheckRespDTO resp = stockCheckService.checkStock(tenantId, items);
        return CommonResult.success(resp);
    }

    private List<SkuQuantityDTO> parseItems(String skuCodes, String quantities) {
        String[] codes = skuCodes.split(",");
        String[] qtyArr = quantities != null && !quantities.isBlank()
                ? quantities.split(",") : new String[0];

        List<SkuQuantityDTO> items = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) {
            String code = codes[i].trim();
            if (code.isEmpty()) continue;

            SkuQuantityDTO dto = new SkuQuantityDTO();
            dto.setSkuCode(code);
            if (i < qtyArr.length) {
                try {
                    dto.setQuantity(new BigDecimal(qtyArr[i].trim()));
                } catch (NumberFormatException e) {
                    dto.setQuantity(BigDecimal.ONE);
                }
            } else {
                dto.setQuantity(BigDecimal.ONE);
            }
            items.add(dto);
        }
        return items;
    }
}
