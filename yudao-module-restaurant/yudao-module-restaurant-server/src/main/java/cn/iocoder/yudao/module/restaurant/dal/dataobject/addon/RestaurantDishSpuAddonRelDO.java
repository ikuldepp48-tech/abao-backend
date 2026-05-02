package cn.iocoder.yudao.module.restaurant.dal.dataobject.addon;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("restaurant_dish_spu_addon_rel")
@KeySequence("restaurant_dish_spu_addon_rel_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantDishSpuAddonRelDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long spuId;

    private Long addonId;

    private Integer relType;

}
