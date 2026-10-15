package cn.iocoder.yudao.module.trade.dal.dataobject.invoice;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
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
 * ⚠️ **没有票种字段**：开**普票**只用到「名称 + 税号」，开**专票**要求五列全有 ——
 * 所以抬头本身不绑票种，**缺项在选专票时才被拒**（判定在 `InvoiceValidator`）。
 * 反过来说，只开普票的客户**不该被迫**填开户行，故除名称外全部可空。
 *
 * ⚠️ 默认抬头的唯一性**在服务层保证**（设新的默认时清掉旧的），不加数据库唯一约束：
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
