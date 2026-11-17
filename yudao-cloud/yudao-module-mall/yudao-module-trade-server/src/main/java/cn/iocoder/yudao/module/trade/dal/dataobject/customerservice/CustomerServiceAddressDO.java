package cn.iocoder.yudao.module.trade.dal.dataobject.customerservice;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 客户服务 - 回收邮寄地址 DO（021）
 *
 * 每租户一条，由 service 层保证。
 */
@TableName("trade_customer_service_address")
@KeySequence("trade_customer_service_address_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerServiceAddressDO extends BaseDO {

    @TableId
    private Long id;
    /** 收件人 */
    private String receiverName;
    /** 联系电话 */
    private String receiverMobile;
    /** 省市区 */
    private String areaName;
    /** 详细地址 */
    private String detailAddress;
    /** 寄件说明 */
    private String shipRemark;

}
