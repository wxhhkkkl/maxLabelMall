package cn.iocoder.yudao.module.trade.dal.dataobject.customerservice;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 客户服务 - 投诉咨询 DO（021）
 */
@TableName("trade_customer_service_ticket")
@KeySequence("trade_customer_service_ticket_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerServiceTicketDO extends BaseDO {

    @TableId
    private Long id;
    /** 会员编号 */
    private Long memberId;
    /** 诉求类型：1 投诉 2 咨询 */
    private Integer type;
    /** 内容 */
    private String content;
    /** 联系手机号 */
    private String contactMobile;
    /** 图片 URL JSON 数组 */
    private String images;
    /** 状态：0 待处理 10 已处理 */
    private Integer status;
    /** 处理说明 */
    private String handleRemark;
    /** 处理人（system_users.id） */
    private Long handlerId;
    /** 处理时间 */
    private LocalDateTime handleTime;
    /** 来源：1 客户自助 2 客服代建 */
    private Integer source;
    /** 创建人（会员或管理员编号） */
    private Long creatorId;

}
