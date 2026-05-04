package cn.iocoder.yudao.module.restaurant.dal.dataobject.order;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 订单操作日志 DO
 */
@TableName("restaurant_order_log")
@KeySequence("restaurant_order_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantOrderLogDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 变更前状态 */
    private Integer fromStatus;

    /** 变更后状态 */
    private Integer toStatus;

    /** 操作方类型：0=顾客 1=系统 2=商家 */
    private Integer operatorType;

    /** 操作方ID（会员ID/用户ID，系统为0） */
    private Long operatorId;

    /** 备注 */
    private String remark;

}
