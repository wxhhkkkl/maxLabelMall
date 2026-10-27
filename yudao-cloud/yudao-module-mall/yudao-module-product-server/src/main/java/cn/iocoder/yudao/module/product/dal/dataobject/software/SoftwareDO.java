package cn.iocoder.yudao.module.product.dal.dataobject.software;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * MaxLabel 软件 / 驱动 DO（013）
 */
@TableName("maxlabel_software")
@KeySequence("maxlabel_software_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoftwareDO extends BaseDO {

    @TableId
    private Long id;
    /** 所属分类，关联 maxlabel_software_category.id */
    private Long categoryId;
    /** 标题 */
    private String title;
    /** 介绍 */
    private String introduction;
    /** 类型：1 桌面端软件 2 打印驱动，见 SoftwareTypeEnum */
    private Integer type;
    /** 是否在服务支持页展示 */
    private Boolean showInSupport;
    /** 状态：0 启用 1 停用 */
    private Integer status;

}
