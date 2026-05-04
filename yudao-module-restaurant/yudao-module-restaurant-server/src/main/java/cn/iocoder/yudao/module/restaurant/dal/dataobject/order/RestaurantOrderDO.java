package cn.iocoder.yudao.module.restaurant.dal.dataobject.order;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("restaurant_order")
@KeySequence("restaurant_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantOrderDO extends TenantBaseDO {

    @TableId
    private Long id;

    private String orderNo;

    private Long memberId;

    private Long storeId;

    private Long tableId;

    private Integer orderType;

    private Integer dinerCount;

    private Integer status;

    private Integer payStatus;

    private BigDecimal originalAmount;

    private BigDecimal discountAmount;

    private BigDecimal payAmount;

    private String remark;

    private String clientOrderNo;

    private Long couponId;

    private Long payOrderId;

    private LocalDateTime payTime;

    private LocalDateTime completeTime;

}
