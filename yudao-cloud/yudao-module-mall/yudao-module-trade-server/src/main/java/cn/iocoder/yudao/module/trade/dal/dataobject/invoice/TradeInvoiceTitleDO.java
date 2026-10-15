package cn.iocoder.yudao.module.trade.dal.dataobject.invoice;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.trade.enums.invoice.InvoiceTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 会员的发票抬头簿，一条抬头可以被两种票复用
 *
 * ⚠️ **有票种字段**（Q36）：一条抬头只服务一种票 —— 普票抬头只要名称、专票抬头五项全填。
 * （2026-10-15 之前是"不绑票种、一条两用"，所有者要求分开管理后推翻了。）
 *
 * ⚠️ 默认抬头的唯一性**按"会员 + 票种"**在服务层保证（设新的默认时清掉**同票种**的旧默认），不加数据库唯一约束：
 * "每个会员至多一条默认、且可以一条都没有"这种约束 MySQL 的部分索引表达不了
 * —— 与既有 `member_address` 的做法一致。
 *
 * @author 芋道源码
 */
@TableName(value = "trade_invoice_title", autoResultMap = true)
@KeySequence("trade_invoice_title_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeInvoiceTitleDO extends BaseDO {

    /**
     * 抬头编号，主键自增
     */
    private Long id;
    /**
     * 会员编号，关联 member_user.id
     */
    private Long userId;
    /**
     * 发票类型
     *
     * ⚠️ 一条抬头**只服务一种票**（Q36，2026-10-15 所有者要求"普票与专票分开管理"）——
     * 原来"不绑票种、一条两用"的设计已推翻。于是校验也按它判：
     * 普票抬头只要名称，专票抬头五项全填。
     *
     * 枚举 {@link InvoiceTypeEnum}
     */
    private Integer type;
    /**
     * 抬头名称（公司名或个人名）
     */
    private String titleName;
    /**
     * 纳税人识别号
     */
    private String taxNo;
    /**
     * 注册地址
     */
    private String titleAddress;
    /**
     * 注册电话
     */
    private String titlePhone;
    /**
     * 开户行及账号
     */
    private String bankName;
    /**
     * 是否默认
     */
    private Boolean defaultStatus;

}
