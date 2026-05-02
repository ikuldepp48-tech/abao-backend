package cn.iocoder.yudao.module.restaurant.dal.dataobject.dish;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * 菜品 SPU DO
 */
@TableName("restaurant_dish_spu")
@KeySequence("restaurant_dish_spu_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantDishSpuDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long categoryId;

    private String name;

    private BigDecimal price;

    private String image;

    private String description;

    private Boolean isSignature;

    private Boolean isNew;

    private Integer sort;

    private Integer status;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

}
