package cn.iocoder.yudao.module.product.dal.dataobject.software;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * MaxLabel 软件分类 DO（013）
 */
@TableName("maxlabel_software_category")
@KeySequence("maxlabel_software_category_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoftwareCategoryDO extends BaseDO {

    @TableId
    private Long id;
    /** 分类名称 */
    private String name;
    /** 排序（升序） */
    private Integer sort;
    /** 状态：0 启用 1 停用 */
    private Integer status;

}
