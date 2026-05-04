package cn.iocoder.yudao.module.restaurant.dal.dataobject.kitchen;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 厨房档口 DO
 */
@TableName("restaurant_kitchen_station")
@KeySequence("restaurant_kitchen_station_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantKitchenStationDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 档口名称 */
    private String name;

    /** 负责的菜品分类ID（JSON数组，如 [1,2,3]） */
    private String dishCategories;

    /** 排序 */
    private Integer sort;

    /** 状态: 0=停用, 1=启用 */
    private Integer status;

}
