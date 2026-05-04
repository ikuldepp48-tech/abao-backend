package cn.iocoder.yudao.module.restaurant.service.printer.listener;

import cn.iocoder.yudao.module.restaurant.service.order.event.OrderPaidEvent;
import cn.iocoder.yudao.module.restaurant.service.printer.PrintTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import jakarta.annotation.Resource;

/**
 * 厨打小票监听 —— 支付成功后触发打印机
 */
@Slf4j
@Component
public class KitchenPrintListener {

    @Resource
    private PrintTaskService printTaskService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        try {
            printTaskService.printOrderForKitchen(event.getOrderId());
        } catch (Exception e) {
            // 打印失败不影响 KDS 推送和订单主流程
            log.error("[KitchenPrintListener][打印失败 orderId({})]", event.getOrderId(), e);
        }
    }

}
