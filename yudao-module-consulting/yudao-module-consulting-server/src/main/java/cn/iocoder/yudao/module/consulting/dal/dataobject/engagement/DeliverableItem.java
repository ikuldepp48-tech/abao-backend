package cn.iocoder.yudao.module.consulting.dal.dataobject.engagement;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 交付物条目（存储在 consulting_engagement.deliverables JSON 字段中）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliverableItem implements Serializable {

    /**
     * 交付物名称
     */
    private String name;

    /**
     * 交付状态（pending/submitted/accepted）
     */
    private String status;

    /**
     * 截止日期
     */
    private LocalDate dueDate;

}
