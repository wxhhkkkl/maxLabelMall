package cn.iocoder.yudao.module.trade.dal.dataobject.customerservice;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 客户服务 - 流转记录 DO（021）
 *
 * 只追加、不修改、不物理删除。
 */
@TableName("trade_customer_service_log")
@KeySequence("trade_customer_service_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerServiceLogDO extends BaseDO {

    @TableId
    private Long id;
    /** 目标类型：1 投诉咨询 2 维修申请 3 维修订单 */
    private Integer targetType;
    /** 目标编号 */
    private Long targetId;
    /** 动作 */
    private String action;
    /** 变更前状态 */
    private Integer fromStatus;
    /** 变更后状态 */
    private Integer toStatus;
    /** 说明（客户可见） */
    private String remark;
    /** 操作人类型：1 会员 2 管理员 3 系统 */
    private Integer operatorType;
    /** 操作人编号 */
    private Long operatorId;

}
