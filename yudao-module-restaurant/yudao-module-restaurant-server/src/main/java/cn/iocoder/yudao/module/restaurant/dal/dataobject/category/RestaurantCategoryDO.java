package cn.iocoder.yudao.module.restaurant.dal.dataobject.category;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 菜品分类 DO
 */
@TableName("restaurant_category")
@KeySequence("restaurant_category_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantCategoryDO extends TenantBaseDO {

    @TableId
    private Long id;

    private String name;

    private Long parentId;

    private Integer sort;

    private Integer status;

}
