package cn.iocoder.yudao.module.restaurant.controller.admin.dish;

import cn.idev.excel.FastExcelFactory;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishImportResultVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishImportVO;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 菜品Excel导入")
@RestController
@RequestMapping("/restaurant/dish-import")
@Validated
public class RestaurantDishImportController {

    @Resource
    private RestaurantDishImportService importService;

    @PostMapping("/upload")
    @Operation(summary = "导入菜品Excel")
    @PreAuthorize("@ss.hasPermission('restaurant:dish:create')")
    public CommonResult<RestaurantDishImportResultVO> importDishes(@RequestParam("file") MultipartFile file) throws IOException {
        List<RestaurantDishImportVO> list = ExcelUtils.read(file, RestaurantDishImportVO.class);
        RestaurantDishImportResultVO result = importService.importDishes(list);
        return success(result);
    }

    @GetMapping("/download-template")
    @Operation(summary = "下载导入模板")
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + URLEncoder.encode("菜品导入模板.xlsx", StandardCharsets.UTF_8));
        FastExcelFactory.write(response.getOutputStream(), RestaurantDishImportVO.class)
                .sheet("菜品").doWrite(List.of());
    }

}
