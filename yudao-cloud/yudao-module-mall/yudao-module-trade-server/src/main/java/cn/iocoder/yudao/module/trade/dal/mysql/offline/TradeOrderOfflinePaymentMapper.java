package cn.iocoder.yudao.module.trade.dal.mysql.offline;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.offline.TradeOrderOfflinePaymentDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface TradeOrderOfflinePaymentMapper extends BaseMapperX<TradeOrderOfflinePaymentDO> {

    /**
     * 按订单编号查询 —— **一个订单至多一条**（`uk_order_id`），所以用 selectOne
     */
    default TradeOrderOfflinePaymentDO selectByOrderId(Long orderId) {
        return selectOne(TradeOrderOfflinePaymentDO::getOrderId, orderId);
    }

    /**
     * 批量查询多个订单的对公信息 —— 列表页要按它判断"是不是对公单"
     *
     * ⚠️ **必须批量**：订单列表一次几十条，逐条查就是 N+1（本仓库已立过这条规律）
     */
    default List<TradeOrderOfflinePaymentDO> selectListByOrderIds(Collection<Long> orderIds) {
        return selectList(TradeOrderOfflinePaymentDO::getOrderId, orderIds);
    }

    /**
     * 该订单是不是对公转账单 —— 只查 id，不取整行（列表页判类型时用得上）
     */
    default boolean existsByOrderId(Long orderId) {
        return selectCount(TradeOrderOfflinePaymentDO::getOrderId, orderId) > 0;
    }

    /**
     * 「待收款」的订单编号集合 —— 给后台订单列表筛选用（FR-209）
     *
     * ⚠️ **为什么不能直接按 `trade_order.pay_channel_code` 筛**：那一列的语义是
     * "**支付成功**的支付渠道"（建表注释如此），对公单在**确认收款前它是空的** ——
     * 所以只能用这张表来判断"是不是对公单"（research R42）。
     */
    default List<Long> selectOrderIdsByConfirmed(boolean confirmed) {
        LambdaQueryWrapper<TradeOrderOfflinePaymentDO> wrapper = new LambdaQueryWrapper<TradeOrderOfflinePaymentDO>()
                .select(TradeOrderOfflinePaymentDO::getOrderId);
        if (confirmed) {
            wrapper.isNotNull(TradeOrderOfflinePaymentDO::getConfirmTime);
        } else {
            wrapper.isNull(TradeOrderOfflinePaymentDO::getConfirmTime);
        }
        return selectList(wrapper).stream().map(TradeOrderOfflinePaymentDO::getOrderId).toList();
    }

}
