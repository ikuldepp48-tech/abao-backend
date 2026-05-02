package cn.iocoder.yudao.module.restaurant.service.menu;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import jakarta.annotation.Resource;

/**
 * 菜单缓存失效监听器 — 事务提交后才执行，防止回滚导致脏缓存
 */
@Slf4j
@Component
public class MenuCacheEvictListener {

    @Resource
    private RestaurantMenuService menuService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMenuChanged(MenuCacheEvictEvent event) {
        if (event.getStoreIds() == null || event.getStoreIds().isEmpty()) {
            return;
        }
        for (Long storeId : event.getStoreIds()) {
            menuService.evictCache(storeId);
        }
    }

}
