package cn.iocoder.yudao.module.restaurant.dal.dataobject.store;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 门店 DO
 */
@TableName("restaurant_store")
@KeySequence("restaurant_store_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantStoreDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long brandId;

    private String name;

    private String code;

    private Integer type;

    private Long parentStoreId;

    private String province;

    private String city;

    private String district;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String phone;

    private Long managerUserId;

    private String businessHours;

    private Integer status;

    private BigDecimal areaSize;

    private Integer seatCount;

    private LocalDate openDate;

    private Boolean supportDineIn;

    private Boolean supportTakeout;

    private Boolean supportPickup;

    private String extraConfig;

}
