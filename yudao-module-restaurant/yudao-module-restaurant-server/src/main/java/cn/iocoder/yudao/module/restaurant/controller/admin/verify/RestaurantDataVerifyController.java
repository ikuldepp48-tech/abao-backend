package cn.iocoder.yudao.module.restaurant.controller.admin.verify;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.restaurant.service.verify.RestaurantDataVerifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.*;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;

@Tag(name = "管理后台 - 数据校验")
@RestController
@RequestMapping("/restaurant/verify")
@Validated
public class RestaurantDataVerifyController {

    @Resource
    private RestaurantDataVerifyService verifyService;

    @ApiAccessLog(operateType = GET)
    @GetMapping("/all")
    @Operation(summary = "校验全部数据")
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<Map<String, Object>> verifyAll() {
        return success(verifyService.verifyAll());
    }

}
