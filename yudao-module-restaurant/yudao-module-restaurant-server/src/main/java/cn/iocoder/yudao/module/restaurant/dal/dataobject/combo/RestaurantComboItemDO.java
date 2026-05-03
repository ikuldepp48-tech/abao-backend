package cn.iocoder.yudao.module.restaurant.dal.dataobject.combo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

@TableName("restaurant_combo_item")
@KeySequence("restaurant_combo_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantComboItemDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long comboId;

    private Long spuId;

    private Long skuId;

    private Integer quantity;

    private BigDecimal extraPrice;

    private Boolean isRequired;

    private String replaceableSpuIds;

    private Integer sort;

}
