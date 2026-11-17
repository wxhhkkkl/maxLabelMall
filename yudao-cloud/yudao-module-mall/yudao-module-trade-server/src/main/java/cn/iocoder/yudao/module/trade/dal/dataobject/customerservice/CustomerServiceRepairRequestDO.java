package cn.iocoder.yudao.module.trade.dal.dataobject.customerservice;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 客户服务 - 维修申请 DO（021）
 *
 * 一份申请只对应一台设备（Q2）。
 */
@TableName("trade_repair_request")
@KeySequence("trade_repair_request_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerServiceRepairRequestDO extends BaseDO {

    @TableId
    private Long id;
    /** 会员编号 */
    private Long memberId;
    /** 设备型号 */
    private String deviceModel;
    /** 故障描述 */
    private String faultDesc;
    /** 联系手机号 */
    private String contactMobile;
    /** 客户返修收件人 */
    private String receiverName;
    /** 客户返修电话 */
    private String receiverMobile;
    /** 客户返修省市区 */
    private String receiverArea;
    /** 客户返修详细地址 */
    private String receiverDetail;
    /** 图片 URL JSON 数组 */
    private String images;
    /** 状态：0 待联系 10 已联系 20 已开单 30 已寄出 40 维修中 50 已完成 60 已取消 */
    private Integer status;
    /** 电话联系时间 */
    private LocalDateTime contactTime;
    /** 联系结果说明 */
    private String contactResult;
    /** 客户寄出快递公司 */
    private String shipCompany;
    /** 客户寄出运单号 */
    private String shipNo;
    /** 客户标记寄出时间 */
    private LocalDateTime shipTime;
    /** 来源：1 客户自助 2 客服代建 */
    private Integer source;
    /** 创建人（会员或管理员编号） */
    private Long creatorId;
    /** 取消原因 */
    private String cancelReason;

}
