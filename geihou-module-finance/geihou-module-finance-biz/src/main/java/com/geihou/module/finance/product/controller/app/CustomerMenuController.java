package com.geihou.module.finance.product.controller.app;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.finance.product.controller.app.vo.MenuSpuDetailVO;
import com.geihou.module.finance.product.controller.app.vo.MenuVO;
import com.geihou.module.finance.product.framework.ProductBusinessException;
import com.geihou.module.finance.product.service.MenuAssemblyService;
import org.springframework.web.bind.annotation.*;

/**
 * Customer menu controller (G1-02H).
 *
 * <p>Provides read-only customer-facing menu endpoints.
 * Base path: /app-api/customer/menu
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /app-api/customer/menu?storeId= — Full menu (category tree with SPUs)</li>
 *   <li>GET /app-api/customer/menu/spu/{id} — SPU detail (SKUs + addon groups + combo items)</li>
 * </ul>
 *
 * <p>storeId is required (PRD OpenAPI required: true) but store-level inventory
 * filtering is not yet implemented (see task package D-3).
 *
 * <p>No StaffMenuController in this slice (D-2: deferred to supplychain module readiness).
 * No MQ consumer, no Dubbo/OpenFeign, no hard delete.
 */
@RestController
@RequestMapping("/app-api/customer/menu")
public class CustomerMenuController {

    private final MenuAssemblyService menuAssemblyService;

    public CustomerMenuController(MenuAssemblyService menuAssemblyService) {
        this.menuAssemblyService = menuAssemblyService;
    }

    /**
     * Get the full customer menu.
     *
     * @param storeId store ID (required)
     * @return CommonResult containing MenuVO
     */
    @GetMapping
    public CommonResult<MenuVO> getMenu(@RequestParam(required = true) Long storeId) {
        MenuVO menu = menuAssemblyService.assembleMenu(storeId);
        return CommonResult.success(menu);
    }

    /**
     * Get SPU detail for customer view.
     *
     * @param id SPU ID
     * @return CommonResult containing MenuSpuDetailVO, or error code if SPU not found / not active
     */
    @GetMapping("/spu/{id}")
    public CommonResult<MenuSpuDetailVO> getSpuDetail(@PathVariable Long id) {
        try {
            MenuSpuDetailVO detail = menuAssemblyService.assembleSpuDetail(id);
            return CommonResult.success(detail);
        } catch (ProductBusinessException e) {
            return CommonResult.error(e.getErrorCode().getCode(), e.getErrorCode().getMsg());
        }
    }
}
