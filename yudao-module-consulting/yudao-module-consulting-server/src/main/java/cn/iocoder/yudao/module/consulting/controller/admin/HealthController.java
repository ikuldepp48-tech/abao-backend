package cn.iocoder.yudao.module.consulting.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.*;

/**
 * 咨询模块 - 健康检查
 */
@Tag(name = "咨询模块 - 健康检查")
@RestController
@RequestMapping("/consulting")
public class HealthController {

    @ApiAccessLog(operateType = GET)
    @GetMapping("/ping")
    @Operation(summary = "健康检查")
    public CommonResult<Map<String, Object>> ping() {
        return CommonResult.success(Map.of(
                "service", "consulting-server",
                "status", "UP",
                "time", LocalDateTime.now().toString()
        ));
    }
}
