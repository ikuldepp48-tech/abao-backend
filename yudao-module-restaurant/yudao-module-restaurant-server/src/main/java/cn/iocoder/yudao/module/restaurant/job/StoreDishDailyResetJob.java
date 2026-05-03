package cn.iocoder.yudao.module.restaurant.job;

import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDishDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreDishMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Component
@Slf4j
public class StoreDishDailyResetJob {

    @Resource
    private RestaurantStoreDishMapper storeDishMapper;

    @Scheduled(cron = "0 0 0 * * ?")
    public void resetTodaySold() {
        int rows = storeDishMapper.update(null,
                new LambdaUpdateWrapper<RestaurantStoreDishDO>()
                        .set(RestaurantStoreDishDO::getTodaySold, 0)
                        .set(RestaurantStoreDishDO::getIsSoldOut, false));
        log.info("每日重置门店菜品已售数据，影响行数: {}", rows);
    }

}
