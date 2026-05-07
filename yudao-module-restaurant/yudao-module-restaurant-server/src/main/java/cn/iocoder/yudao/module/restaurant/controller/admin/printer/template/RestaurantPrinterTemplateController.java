package cn.iocoder.yudao.module.restaurant.controller.admin.printer.template;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.printer.template.vo.RestaurantPrinterTemplateRespVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.printer.template.vo.RestaurantPrinterTemplateSaveReqVO;
import cn.iocoder.yudao.module.restaurant.service.printer.RestaurantPrinterTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 打印模板配置")
@RestController
@RequestMapping("/restaurant/printer-template")
@Validated
public class RestaurantPrinterTemplateController {

    @Resource
    private RestaurantPrinterTemplateService templateService;

    @GetMapping("/get-by-printer")
    @Operation(summary = "根据打印机获取模板")
    @Parameter(name = "printerId", description = "打印机ID", required = true)
    @PreAuthorize("@ss.hasPermission('restaurant:printer:query')")
    public CommonResult<RestaurantPrinterTemplateRespVO> getByPrinterId(@RequestParam("printerId") Long printerId) {
        return success(templateService.getByPrinterId(printerId));
    }

    @PutMapping("/save")
    @Operation(summary = "保存打印模板")
    @PreAuthorize("@ss.hasPermission('restaurant:printer:update')")
    public CommonResult<Boolean> saveTemplate(@Valid @RequestBody RestaurantPrinterTemplateSaveReqVO reqVO) {
        templateService.saveTemplate(reqVO);
        return success(true);
    }

}
