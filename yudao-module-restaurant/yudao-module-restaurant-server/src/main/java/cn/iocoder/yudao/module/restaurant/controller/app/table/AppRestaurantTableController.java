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
    @Operation(summary = "扫码识别桌台（加密 token）")
    @Parameter(name = "token", description = "二维码中的加密token", required = true)
    @PermitAll
    public CommonResult<RestaurantTableScanRespVO> scanTable(@RequestParam("token") String token) {
        return success(tableService.scanByToken(token));
    }

}
