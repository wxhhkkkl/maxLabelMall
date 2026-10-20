package cn.iocoder.yudao.module.member.dal.dataobject.template;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * MaxLabel 会员私有云端模板 DO
 */
@TableName("maxlabel_label_template")
@KeySequence("maxlabel_label_template_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxLabelLabelTemplateDO extends BaseDO {

    @TableId
    private Long id;
    private Long memberId;
    private String name;
    /** .msdx JSON 原文 */
    private String content;
    private Integer sizeBytes;
    private String contentHash;
    private BigDecimal widthMm;
    private BigDecimal heightMm;
    private String category;
    private String keywords;

}
