package cn.iocoder.yudao.module.restaurant.controller.app.table;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.restaurant.controller.app.table.vo.RestaurantTableScanRespVO;
import cn.iocoder.yudao.module.restaurant.service.table.RestaurantTableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "顾客端 - 桌台扫码")
@RestController
@RequestMapping("/restaurant/table")
@Validated
@TenantIgnore
public class AppRestaurantTableController {

    @Resource
    private RestaurantTableService tableService;

    @GetMapping("/scan")
    @Operation(summary = "扫码识别桌台")
    @Parameter(name = "storeId", description = "门店ID", required = true)
    @Parameter(name = "tableId", description = "桌台ID", required = true)
    @PermitAll
    public CommonResult<RestaurantTableScanRespVO> scanTable(@RequestParam("storeId") Long storeId,
                                                              @RequestParam("tableId") Long tableId) {
        return success(tableService.scanTable(storeId, tableId));
    }

}
