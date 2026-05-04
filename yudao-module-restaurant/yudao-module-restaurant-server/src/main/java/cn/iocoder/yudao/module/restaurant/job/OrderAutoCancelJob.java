package cn.iocoder.yudao.module.restaurant.job;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderMapper;
import cn.iocoder.yudao.module.restaurant.service.order.RestaurantOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderAutoCancelJob {

    @Resource
    private RestaurantOrderMapper orderMapper;

    @Resource
    private RestaurantOrderService orderService;

    @Scheduled(cron = "0 */5 * * * ?")
    public void autoCancelUnpaidOrders() {
        TenantUtils.executeIgnore(() -> {
            // 查询超时15分钟未支付的订单（所有租户）
            List<RestaurantOrderDO> unpaidOrders = orderMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RestaurantOrderDO>()
                            .eq(RestaurantOrderDO::getStatus, 0)
                            .lt(RestaurantOrderDO::getCreateTime, LocalDateTime.now().minusMinutes(15)));

            if (unpaidOrders.isEmpty()) {
                return;
            }

            log.info("扫描到 {} 笔超时未支付订单，开始自动取消", unpaidOrders.size());
            for (RestaurantOrderDO order : unpaidOrders) {
                try {
                    orderService.cancelOrderBySystem(order.getId());
                } catch (Exception e) {
                    log.error("[autoCancelUnpaidOrders][取消订单失败 orderId({})]", order.getId(), e);
                }
            }
            log.info("自动取消超时订单完成");
        });
    }

}
