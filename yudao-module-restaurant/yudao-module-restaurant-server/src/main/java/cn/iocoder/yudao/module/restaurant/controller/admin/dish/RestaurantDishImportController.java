package cn.iocoder.yudao.module.restaurant.controller.admin.dish;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishImportResultVO;
import cn.iocoder.yudao.module.restaurant.service.dish.RestaurantDishImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.IOException;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 菜品Excel导入")
@RestController
@RequestMapping("/restaurant/dish-import")
@Validated
public class RestaurantDishImportController {

    @Resource
    private RestaurantDishImportService importService;

    @PostMapping("/upload")
    @Operation(summary = "导入菜品Excel（多Sheet）")
    @ApiAccessLog(operateType = IMPORT)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:create')")
    public CommonResult<RestaurantDishImportResultVO> importDishes(@RequestParam("file") MultipartFile file) throws IOException {
        RestaurantDishImportResultVO result = importService.importDishes(file);
        return success(result);
    }

    @GetMapping("/download-template")
    @Operation(summary = "下载导入模板（4个Sheet）")
    @ApiAccessLog(operateType = EXPORT)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        importService.generateTemplate(response);
    }

}
