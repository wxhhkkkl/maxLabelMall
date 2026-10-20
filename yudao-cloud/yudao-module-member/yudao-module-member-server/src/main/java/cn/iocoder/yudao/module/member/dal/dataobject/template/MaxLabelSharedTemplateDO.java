package cn.iocoder.yudao.module.member.dal.dataobject.template;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MaxLabel 平台共享模板（清洗后的副本）DO
 */
@TableName("maxlabel_shared_template")
@KeySequence("maxlabel_shared_template_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxLabelSharedTemplateDO extends BaseDO {

    public static final int AUDIT_PENDING = 0;
    public static final int AUDIT_APPROVED = 1;
    public static final int AUDIT_REJECTED = 2;
    public static final int AUDIT_OFFLINE = 3;

    @TableId
    private Long id;
    private Long sourceTemplateId;
    private Long publisherMemberId;
    private String name;
    private String content;
    private Integer sizeBytes;
    private BigDecimal widthMm;
    private BigDecimal heightMm;
    private String category;
    private String keywords;
    private Integer auditStatus;
    private String rejectReason;
    private Long auditUserId;
    private LocalDateTime auditTime;
    private LocalDateTime publishedTime;

}
