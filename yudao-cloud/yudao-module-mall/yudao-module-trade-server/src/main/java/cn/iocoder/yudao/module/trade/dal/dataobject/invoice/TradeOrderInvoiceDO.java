package cn.iocoder.yudao.module.trade.dal.dataobject.invoice;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.enums.invoice.InvoiceTypeEnum;
import cn.iocoder.yudao.module.trade.enums.invoice.TradeOrderInvoiceStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 交易订单的开票申请，与 {@link TradeOrderDO} **一一对应**（`uk_order_id`）
 *
 * 产生于两处入口：客户**下单时**勾选开票（与订单同一事务），或事后在**订单详情补开**。
 *
 * ⚠️ 抬头的五个字段是**申请时的快照**，不引用会员的发票抬头簿 ——
 * 所以客户改 / 删抬头**结构上不可能**影响历史申请（FR-158 / SC-061）。
 *
 * ⚠️ 这里**没有**"订单是否已支付"的字段：它由订单裁决。后台「待开票」列表的分页
 * join `trade_order` 并带 `pay_status = 1` 条件；在此存第二份会出现两份真相。
 *
 * @author 芋道源码
 */
@TableName(value = "trade_order_invoice", autoResultMap = true)
@KeySequence("trade_order_invoice_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeOrderInvoiceDO extends BaseDO {

    /**
     * 申请编号，主键自增
     */
    private Long id;
    /**
     * 订单编号，关联 {@link TradeOrderDO#getId()}
     */
    private Long orderId;
    /**
     * 订单号（下单时抄一份，此后不再更新）
     *
     * 仅用于后台列表展示与排查，省一次回查订单表 —— 与 {@code TradeAfterSaleDO#getOrderNo()} 同一做法
     */
    private String orderNo;
    /**
     * 会员编号，关联 member_user.id
     */
    private Long userId;
    /**
     * 发票类型
     *
     * 枚举 {@link InvoiceTypeEnum}
     */
    private Integer type;
    /**
     * 状态
     *
     * 枚举 {@link TradeOrderInvoiceStatusEnum}
     */
    private Integer status;
    /**
     * 开票金额，单位：分
     *
     * = **申请那一刻**的订单实付金额（FR-161）。服务端从订单读，不接受请求传入。
     * ⚠️ 之后订单发生**部分退款**也不会改它（Q33）—— 那是运营开具前要判断的事。
     */
    private Integer amount;
    /**
     * 抬头名称（公司名或个人名）
     */
    private String titleName;
    /**
     * 纳税人识别号
     *
     * 专票必填；普票选填（填了就要通过格式校验）
     */
    private String taxNo;
    /**
     * 注册地址（专票必填）
     */
    private String titleAddress;
    /**
     * 注册电话（专票必填）
     */
    private String titlePhone;
    /**
     * 开户行及账号（专票必填）
     */
    private String bankName;
    /**
     * 开好的发票文件地址
     *
     * ⚠️ **只留最新一份**（Q31）：重新上传是覆盖，不保留历次版本。
     * 文件本体在既有对象存储上，链接公网可读（spec 的 Assumptions 已记录该风险）。
     */
    private String invoiceFileUrl;
    /**
     * 开票时间
     */
    private LocalDateTime issueTime;
    /**
     * 开具人（后台操作者），关联 system_users.id
     */
    private Long issueUserId;
    /**
     * 作废时间
     */
    private LocalDateTime cancelTime;

}
