package cn.iocoder.yudao.module.member.dal.dataobject.org;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * MaxLabel 企业主体 DO
 */
@TableName("maxlabel_org")
@KeySequence("maxlabel_org_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxLabelOrgDO extends BaseDO {

    @TableId
    private Long id;
    /** 企业名称 */
    private String name;
    /** 企业 OWNER，关联 member_user.id */
    private Long ownerMemberId;
    /** 状态：0 启用 1 停用，见 MaxLabelOrgStatusEnum */
    private Integer status;
    /** 备注 */
    private String remark;

}
