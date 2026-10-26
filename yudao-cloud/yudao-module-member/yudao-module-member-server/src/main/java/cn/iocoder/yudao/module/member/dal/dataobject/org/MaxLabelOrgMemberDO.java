package cn.iocoder.yudao.module.member.dal.dataobject.org;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MaxLabel 企业成员关系 DO
 *
 * 退出/移出为**物理删除**；唯一键 (tenant_id, member_id) 保证一人一企业。
 */
@TableName("maxlabel_org_member")
@KeySequence("maxlabel_org_member_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxLabelOrgMemberDO extends BaseDO {

    @TableId
    private Long id;
    /** 企业编号，关联 maxlabel_org.id */
    private Long orgId;
    /** 会员编号，关联 member_user.id */
    private Long memberId;
    /** 角色：1 OWNER 2 ADMIN 3 MEMBER，见 MaxLabelOrgRoleEnum */
    private Integer role;
    /** 加入时间 */
    private LocalDateTime joinedTime;

}
