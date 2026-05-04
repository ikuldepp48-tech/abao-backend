package cn.iocoder.yudao.module.restaurant.dal.dataobject.order;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

@TableName("restaurant_order_item")
@KeySequence("restaurant_order_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantOrderItemDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long orderId;

    private Long spuId;

    private String spuName;

    private Long skuId;

    private String skuName;

    private BigDecimal unitPrice;

    private Integer quantity;

    private String addonsJson;

    private String customerRemark;

    private BigDecimal subtotal;

    /** KDS状态: 0=待制作, 1=制作中, 2=已出餐 */
    private Integer kdsStatus;

}
