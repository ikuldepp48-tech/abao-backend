package cn.iocoder.yudao.module.restaurant.dal.dataobject.printer;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 打印机配置 DO
 */
@TableName("restaurant_printer")
@KeySequence("restaurant_printer_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantPrinterDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 打印机名称 */
    private String name;

    /** 类型: 1=厨打 2=客联 */
    private Integer type;

    /** 厂商: mock/feieyun/yiliantong/xinye */
    private String provider;

    /** 设备号 */
    private String deviceNo;

    /** 设备密钥 */
    private String deviceKey;

    /** 所属门店 */
    private Long storeId;

    /** 状态: 0=离线 1=在线 */
    private Integer status;

}
