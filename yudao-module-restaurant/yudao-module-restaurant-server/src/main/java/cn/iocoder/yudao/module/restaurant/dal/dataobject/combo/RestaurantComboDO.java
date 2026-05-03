package cn.iocoder.yudao.module.restaurant.dal.dataobject.combo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

@TableName("restaurant_combo")
@KeySequence("restaurant_combo_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantComboDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long brandId;

    private String name;

    private String image;

    private String description;

    private BigDecimal comboPrice;

    private BigDecimal originalPrice;

    private Integer sort;

    private Integer status;

    private Boolean validForDineIn;

    private Boolean validForTakeout;

}
