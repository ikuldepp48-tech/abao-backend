package cn.iocoder.yudao.module.restaurant.dal.dataobject.table;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 桌台 DO
 */
@TableName("restaurant_table")
@KeySequence("restaurant_table_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantTableDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long storeId;

    private String area;

    private String tableNo;

    private Integer seatCapacity;

    private String qrCode;

    private Integer status;

    private Long currentOrderId;

    private LocalDateTime openTime;

}
