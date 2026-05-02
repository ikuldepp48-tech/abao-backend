package cn.iocoder.yudao.module.restaurant.dal.dataobject.addon;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

@TableName("restaurant_dish_addon")
@KeySequence("restaurant_dish_addon_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantDishAddonDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long brandId;

    private String groupName;

    private String name;

    private BigDecimal extraPrice;

    private Boolean isRequired;

    private Boolean isMulti;

    private Integer sort;

    private Integer status;

}
