package cn.iocoder.yudao.module.trade.dal.dataobject.offline;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
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
 * 对公转账信息，与 {@link TradeOrderDO} **一一对应**（`uk_order_id`）（008）
 *
 * **下单时插入**（只有客户选了「对公转账」才插），**确认收款时填上**后半部分字段。
 *
 * ⚠️ 这张表**同时回答两个问题**，所以别再为其中任何一个另加字段：
 *   ① **本单是不是对公转账** —— 看**有没有这一行**（而不是看某个标记位）
 *   ② **财务凭哪笔流水确认的** —— 看行的内容（流水号 / 打款方 / 到账时间 / 确认人 / 确认时间）
 *
 * ⚠️ **为什么建子表，而不是给 `trade_order` 加列 / 加状态值**：
 * `trade_order` 是全局最热的表（详情/列表/售后/统计都查它，已有 40+ 列），006 已经立过
 * "建子表而不是加列"的先例；而 `TradeOrderStatusEnum` 全仓有 ~76 处引用，
 * 为一个"待收款"加状态值代价与收益完全不成比例。详见 `specs/008-corporate-transfer/research.md` R42。
 *
 * ⚠️ **"还没确认"用 `confirmTime == null` 表达**，不另设状态列 ——
 * "有没有值"本身就是状态（与 007 用 `delivery_types` 的取值表达"虚拟商品"同一个思路）。
 *
 * @author 芋道源码
 */
@TableName(value = "trade_order_offline_payment", autoResultMap = true)
@KeySequence("trade_order_offline_payment_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeOrderOfflinePaymentDO extends BaseDO {

    /**
     * 编号，主键自增
     */
    private Long id;
    /**
     * 订单编号，关联 {@link TradeOrderDO#getId()}
     */
    private Long orderId;
    /**
     * 银行流水号
     *
     * ⚠️ **确认收款时必填**（FR-204）—— 但列上不设非空约束：
     * 下单那一刻它本来就是空的，"必填"是**确认收款这个动作**的约束，写在服务层。
     * 有了它，事后才能把这笔单和银行流水对上账。
     */
    private String bankSerialNo;
    /**
     * 打款方名称（选填）
     *
     * 对公打款的**付款方**未必与抬头一致（母公司代付、财务公司代付都常见），
     * 记下来对账和开票时用得上。
     */
    private String payerName;
    /**
     * 到账时间（选填）—— 财务照银行流水填
     */
    private LocalDateTime paidTime;
    /**
     * 备注（选填）
     */
    private String remark;
    /**
     * 确认人（后台操作者），关联 system_users.id
     */
    private Long confirmUserId;
    /**
     * 确认时间
     *
     * ⚠️ **为空即"还没确认"** —— 本表不另设状态列，见类注释
     */
    private LocalDateTime confirmTime;

}
