package cn.iocoder.yudao.module.restaurant.dal.dataobject.printer;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("restaurant_printer_template")
@KeySequence("restaurant_printer_template_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantPrinterTemplateDO extends BaseDO {

    @TableId
    private Long id;

    private Long printerId;

    private Integer paperWidth;

    private String headerText;

    private String footerText;

    private Integer showLogo;

    private Integer showQr;

    private Integer autoCut;

    private Integer printCopies;

}
