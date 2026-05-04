package cn.iocoder.yudao.module.restaurant.controller.admin.printer;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.printer.vo.RestaurantPrinterSaveReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.printer.RestaurantPrinterDO;
import cn.iocoder.yudao.module.restaurant.service.printer.RestaurantPrinterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 打印机管理")
@RestController
@RequestMapping("/restaurant/printer")
@Validated
public class RestaurantPrinterController {

    @Resource
    private RestaurantPrinterService printerService;

    @PostMapping("/create")
    @Operation(summary = "创建打印机")
    @PreAuthorize("@ss.hasPermission('restaurant:printer:create')")
    public CommonResult<Long> createPrinter(@Valid @RequestBody RestaurantPrinterSaveReqVO reqVO) {
        return success(printerService.createPrinter(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新打印机")
    @PreAuthorize("@ss.hasPermission('restaurant:printer:update')")
    public CommonResult<Boolean> updatePrinter(@Valid @RequestBody RestaurantPrinterSaveReqVO reqVO) {
        printerService.updatePrinter(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除打印机")
    @PreAuthorize("@ss.hasPermission('restaurant:printer:delete')")
    public CommonResult<Boolean> deletePrinter(@RequestParam("id") Long id) {
        printerService.deletePrinter(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取打印机详情")
    @PreAuthorize("@ss.hasPermission('restaurant:printer:query')")
    public CommonResult<RestaurantPrinterDO> getPrinter(@RequestParam("id") Long id) {
        return success(printerService.getPrinter(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获取打印机分页")
    @PreAuthorize("@ss.hasPermission('restaurant:printer:query')")
    public CommonResult<PageResult<RestaurantPrinterDO>> getPrinterPage(@RequestParam(defaultValue = "1") Integer pageNo,
                                                                        @RequestParam(defaultValue = "10") Integer pageSize) {
        return success(printerService.getPrinterPage(pageNo, pageSize));
    }

}
