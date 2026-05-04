package cn.iocoder.yudao.module.restaurant.controller.admin.notify;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pay.api.notify.dto.PayOrderNotifyReqDTO;
import cn.iocoder.yudao.module.restaurant.service.order.RestaurantOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 支付回调通知")
@RestController
@RequestMapping("/restaurant/notify")
@Slf4j
public class PayNotifyController {

    @Resource
    private RestaurantOrderService orderService;

    @PostMapping("/pay-success")
    @Operation(summary = "支付成功回调")
    @PermitAll
    public CommonResult<Boolean> onPaySuccess(@Valid @RequestBody PayOrderNotifyReqDTO reqDTO) {
        log.info("[onPaySuccess][merchantOrderId({}) payOrderId({})]", reqDTO.getMerchantOrderId(), reqDTO.getPayOrderId());
        orderService.onPaySuccess(reqDTO.getMerchantOrderId(), reqDTO.getPayOrderId());
        return success(true);
    }

}
