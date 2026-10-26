package cn.iocoder.yudao.module.member.dal.dataobject.org;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MaxLabel 企业席位分配 DO
 *
 * 席位**不绑定具体授权批次**（Q71）；撤销后复用同一行。
 */
@TableName("maxlabel_org_seat")
@KeySequence("maxlabel_org_seat_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxLabelOrgSeatDO extends BaseDO {

    @TableId
    private Long id;
    private Long orgId;
    private Long memberId;
    /** 状态：0 有效 1 已撤销，见 MaxLabelOrgSeatStatusEnum */
    private Integer status;
    /** 操作人类型：1 会员 2 系统用户 */
    private Integer operatorType;
    /** 操作人编号（会员或 system_users.id） */
    private Long operatorId;
    /** 最近一次分配时间 */
    private LocalDateTime assignedTime;
    /** 撤销时间 */
    private LocalDateTime revokedTime;

}
