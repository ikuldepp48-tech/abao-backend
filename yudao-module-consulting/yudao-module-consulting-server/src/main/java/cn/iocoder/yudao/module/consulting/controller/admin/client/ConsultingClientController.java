package cn.iocoder.yudao.module.consulting.controller.admin.client;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.*;
import cn.iocoder.yudao.module.consulting.convert.client.ConsultingClientConvert;
import cn.iocoder.yudao.module.consulting.dal.dataobject.client.ConsultingClientDO;
import cn.iocoder.yudao.module.consulting.service.client.ConsultingClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.*;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;

@Tag(name = "管理后台 - 咨询客户档案")
@RestController
@RequestMapping("/consulting/client")
@Validated
public class ConsultingClientController {

    @Resource
    private ConsultingClientService clientService;

    @ApiAccessLog(operateType = CREATE)
    @PostMapping("/create")
    @Operation(summary = "创建咨询客户档案")
    @PreAuthorize("@ss.hasPermission('consulting:client:create')")
    public CommonResult<Long> createClient(@Valid @RequestBody ConsultingClientCreateReqVO createReqVO) {
        return success(clientService.createClient(createReqVO));
    }

    @ApiAccessLog(operateType = UPDATE)
    @PutMapping("/update")
    @Operation(summary = "更新咨询客户档案")
    @PreAuthorize("@ss.hasPermission('consulting:client:update')")
    public CommonResult<Boolean> updateClient(@Valid @RequestBody ConsultingClientUpdateReqVO updateReqVO) {
        clientService.updateClient(updateReqVO);
        return success(true);
    }

    @ApiAccessLog(operateType = DELETE)
    @DeleteMapping("/delete")
    @Operation(summary = "删除咨询客户档案")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('consulting:client:delete')")
    public CommonResult<Boolean> deleteClient(@RequestParam("id") Long id) {
        clientService.deleteClient(id);
        return success(true);
    }

    @ApiAccessLog(operateType = GET)
    @GetMapping("/get")
    @Operation(summary = "获得咨询客户档案")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('consulting:client:query')")
    public CommonResult<ConsultingClientRespVO> getClient(@RequestParam("id") Long id) {
        ConsultingClientDO client = clientService.getClient(id);
        return success(ConsultingClientConvert.INSTANCE.convert(client));
    }

    @ApiAccessLog(operateType = GET)
    @GetMapping("/list-all-simple")
    @Operation(summary = "获取咨询客户档案精简信息列表", description = "主要用于前端的下拉选项")
    public CommonResult<List<ConsultingClientRespVO>> getSimpleClientList() {
        List<ConsultingClientDO> list = clientService.getClientList();
        return success(ConsultingClientConvert.INSTANCE.convertList(list));
    }

    @ApiAccessLog(operateType = GET)
    @GetMapping("/list")
    @Operation(summary = "获得咨询客户档案列表")
    @Parameter(name = "ids", description = "编号列表", required = true, example = "1,2")
    @PreAuthorize("@ss.hasPermission('consulting:client:query')")
    public CommonResult<List<ConsultingClientRespVO>> getClientList(@RequestParam("ids") Collection<Long> ids) {
        List<ConsultingClientDO> list = clientService.getClientList(ids);
        return success(ConsultingClientConvert.INSTANCE.convertList(list));
    }

    @ApiAccessLog(operateType = GET)
    @GetMapping("/page")
    @Operation(summary = "获得咨询客户档案分页")
    @PreAuthorize("@ss.hasPermission('consulting:client:query')")
    public CommonResult<PageResult<ConsultingClientRespVO>> getClientPage(@Valid ConsultingClientPageReqVO pageVO) {
        PageResult<ConsultingClientDO> pageResult = clientService.getClientPage(pageVO);
        return success(ConsultingClientConvert.INSTANCE.convertPage(pageResult));
    }

}
