package cn.iocoder.yudao.module.consulting.controller.admin.client;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.*;
import cn.iocoder.yudao.module.consulting.convert.client.ConsultingClientContactConvert;
import cn.iocoder.yudao.module.consulting.dal.dataobject.client.ConsultingClientContactDO;
import cn.iocoder.yudao.module.consulting.service.client.ConsultingClientContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 咨询客户联系人")
@RestController
@RequestMapping("/consulting/client-contact")
@Validated
public class ConsultingClientContactController {

    @Resource
    private ConsultingClientContactService contactService;

    @PostMapping("/create")
    @Operation(summary = "创建联系人")
    @PreAuthorize("@ss.hasPermission('consulting:client-contact:create')")
    public CommonResult<Long> createContact(@Valid @RequestBody ConsultingClientContactCreateReqVO createReqVO) {
        return success(contactService.createContact(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新联系人")
    @PreAuthorize("@ss.hasPermission('consulting:client-contact:update')")
    public CommonResult<Boolean> updateContact(@Valid @RequestBody ConsultingClientContactUpdateReqVO updateReqVO) {
        contactService.updateContact(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除联系人")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('consulting:client-contact:delete')")
    public CommonResult<Boolean> deleteContact(@RequestParam("id") Long id) {
        contactService.deleteContact(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得联系人")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('consulting:client-contact:query')")
    public CommonResult<ConsultingClientContactRespVO> getContact(@RequestParam("id") Long id) {
        ConsultingClientContactDO contact = contactService.getContact(id);
        return success(ConsultingClientContactConvert.INSTANCE.convert(contact));
    }

    @GetMapping("/page")
    @Operation(summary = "获得联系人分页")
    @PreAuthorize("@ss.hasPermission('consulting:client-contact:query')")
    public CommonResult<PageResult<ConsultingClientContactRespVO>> getContactPage(@Valid ConsultingClientContactPageReqVO pageVO) {
        PageResult<ConsultingClientContactDO> pageResult = contactService.getContactPage(pageVO);
        return success(ConsultingClientContactConvert.INSTANCE.convertPage(pageResult));
    }

}
