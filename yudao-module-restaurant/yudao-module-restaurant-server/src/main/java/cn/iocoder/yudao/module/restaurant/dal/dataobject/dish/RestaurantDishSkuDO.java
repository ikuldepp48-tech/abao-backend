package cn.iocoder.yudao.module.restaurant.dal.dataobject.dish;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * 菜品 SKU DO
 */
@TableName("restaurant_dish_sku")
@KeySequence("restaurant_dish_sku_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantDishSkuDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long spuId;

    private String name;

    private String properties;

    private BigDecimal price;

    private BigDecimal memberPrice;

    private BigDecimal costPrice;

    private Integer weight;

    private String barcode;

    private String picture;

    private Integer sort;

    private Integer status;

}
