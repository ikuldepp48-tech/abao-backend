package cn.iocoder.yudao.module.restaurant.dal.dataobject.store;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

@TableName("restaurant_store_dish")
@KeySequence("restaurant_store_dish_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantStoreDishDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long storeId;

    private Long spuId;

    private Boolean isAvailable;

    private Integer dailyLimit;

    private Boolean isSoldOut;

    private Integer todaySold;

    private BigDecimal price;

    private Integer sort;

    private Integer status;

}
