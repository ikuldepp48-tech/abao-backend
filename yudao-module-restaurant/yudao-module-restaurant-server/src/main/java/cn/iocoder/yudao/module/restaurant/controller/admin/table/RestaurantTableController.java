package cn.iocoder.yudao.module.restaurant.controller.admin.table;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.table.vo.*;
import cn.iocoder.yudao.module.restaurant.convert.table.RestaurantTableConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.table.RestaurantTableDO;
import cn.iocoder.yudao.module.restaurant.service.table.RestaurantTableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 桌台")
@RestController
@RequestMapping("/restaurant/table")
@Validated
public class RestaurantTableController {

    @Resource
    private RestaurantTableService tableService;

    @PostMapping("/create")
    @Operation(summary = "创建桌台")
    @ApiAccessLog(operateType = CREATE)
    @PreAuthorize("@ss.hasPermission('restaurant:table:create')")
    public CommonResult<Long> createTable(@Valid @RequestBody RestaurantTableCreateReqVO createReqVO) {
        return success(tableService.createTable(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新桌台")
    @ApiAccessLog(operateType = UPDATE)
    @PreAuthorize("@ss.hasPermission('restaurant:table:update')")
    public CommonResult<Boolean> updateTable(@Valid @RequestBody RestaurantTableUpdateReqVO updateReqVO) {
        tableService.updateTable(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除桌台")
    @Parameter(name = "id", description = "编号", required = true)
    @ApiAccessLog(operateType = DELETE)
    @PreAuthorize("@ss.hasPermission('restaurant:table:delete')")
    public CommonResult<Boolean> deleteTable(@RequestParam("id") Long id) {
        tableService.deleteTable(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得桌台")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:table:query')")
    public CommonResult<RestaurantTableRespVO> getTable(@RequestParam("id") Long id) {
        RestaurantTableDO table = tableService.getTable(id);
        return success(RestaurantTableConvert.INSTANCE.convert(table));
    }

    @GetMapping("/list-all-simple")
    @Operation(summary = "获取桌台精简信息列表", description = "主要用于前端的下拉选项")
    @ApiAccessLog(operateType = GET)
    public CommonResult<List<RestaurantTableRespVO>> getSimpleTableList() {
        List<RestaurantTableDO> list = tableService.getTableList();
        return success(RestaurantTableConvert.INSTANCE.convertList(list));
    }

    @GetMapping("/list")
    @Operation(summary = "获得桌台列表")
    @Parameter(name = "ids", description = "编号列表", required = true, example = "1,2")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:table:query')")
    public CommonResult<List<RestaurantTableRespVO>> getTableList(@RequestParam("ids") Collection<Long> ids) {
        List<RestaurantTableDO> list = tableService.getTableList(ids);
        return success(RestaurantTableConvert.INSTANCE.convertList(list));
    }

    @GetMapping("/page")
    @Operation(summary = "获得桌台分页")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:table:query')")
    public CommonResult<PageResult<RestaurantTableRespVO>> getTablePage(@Valid RestaurantTablePageReqVO pageVO) {
        PageResult<RestaurantTableDO> pageResult = tableService.getTablePage(pageVO);
        return success(RestaurantTableConvert.INSTANCE.convertPage(pageResult));
    }

    @PostMapping("/batch-create")
    @Operation(summary = "批量创建桌台")
    @ApiAccessLog(operateType = CREATE)
    @PreAuthorize("@ss.hasPermission('restaurant:table:create')")
    public CommonResult<Map<String, Integer>> batchCreateTable(@Valid @RequestBody RestaurantTableBatchCreateReqVO reqVO) {
        return success(tableService.batchCreateTable(reqVO));
    }

    @GetMapping("/generate-qr")
    @Operation(summary = "生成桌台二维码图片")
    @ApiAccessLog(operateType = OTHER)
    @PreAuthorize("@ss.hasPermission('restaurant:table:update')")
    public void generateQrCode(@RequestParam("id") Long id, HttpServletResponse response) throws IOException {
        byte[] qrPng = tableService.generateQrCode(id);
        response.setContentType("image/png");
        response.getOutputStream().write(qrPng);
        response.getOutputStream().flush();
    }

    @GetMapping("/export-qr-pdf")
    @Operation(summary = "批量导出桌台二维码PDF")
    @ApiAccessLog(operateType = EXPORT)
    @PreAuthorize("@ss.hasPermission('restaurant:table:query')")
    public void exportQrCodePdf(@RequestParam("storeId") Long storeId, HttpServletResponse response) throws IOException {
        byte[] pdfBytes = tableService.exportQrCodePdf(storeId);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=table-qr-codes.pdf");
        response.getOutputStream().write(pdfBytes);
        response.getOutputStream().flush();
    }

}
