package cn.iocoder.yudao.module.trade.dal.dataobject.delivery;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 快递公司 DO
 *
 * @author jason
 */
@TableName(value ="trade_delivery_express")
@KeySequence("trade_delivery_express_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
public class DeliveryExpressDO extends BaseDO {

    /**
     * 编号，自增
     */
    @TableId
    private Long id;

    /**
     * 快递公司 code
     *
     * ⚠️ 它**同时**被微信小程序发货信息同步当作 {@code expressCompany} 上送，
     * 所以是 `SF / STO / ZTO / YD` 这套短码。**不要**把它改成快递100 的全拼编码。
     */
    private String code;

    /**
     * 快递100 的公司编码（实时查询的 {@code com} 参数），如 {@code shunfeng}
     *
     * 为空 = 该快递公司暂不支持物流查询（例如分不清产品线的「安能」）。
     */
    private String kd100Code;

    /**
     * 快递公司名称
     */
    private String name;

    /**
     * 快递公司 logo
     */
    private String logo;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    // TODO 芋艿：c 和结算相关的字段，后续在看
    //    partnerId	是否需要月结账号
    //    partnerKey	是否需要月结密码
    //    net	是否需要取件网店
    //    account	账号
    //    password	网点名称
    //    isShow	是否显示
}
