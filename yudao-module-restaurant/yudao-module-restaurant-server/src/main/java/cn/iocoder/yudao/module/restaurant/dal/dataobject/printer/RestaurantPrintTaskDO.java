package cn.iocoder.yudao.module.restaurant.dal.dataobject.printer;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 打印任务 DO
 */
@TableName("restaurant_print_task")
@KeySequence("restaurant_print_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantPrintTaskDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 打印机ID */
    private Long printerId;

    /** 订单ID */
    private Long orderId;

    /** 打印内容 */
    private String content;

    /** 状态: 0=待打印 1=成功 2=失败 */
    private Integer status;

    /** 重试次数 */
    private Integer retryCount;

    /** 错误信息 */
    private String errorMsg;

    /** 打印时间 */
    private LocalDateTime printTime;

}
