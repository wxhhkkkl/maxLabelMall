package cn.iocoder.yudao.module.member.dal.dataobject.share;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * MaxLabel 平台共享内容分类 DO
 */
@TableName("maxlabel_platform_content_category")
@KeySequence("maxlabel_platform_content_category_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxLabelPlatformContentCategoryDO extends BaseDO {

    @TableId
    private Long id;
    private String name;
    private Integer sort;

}
