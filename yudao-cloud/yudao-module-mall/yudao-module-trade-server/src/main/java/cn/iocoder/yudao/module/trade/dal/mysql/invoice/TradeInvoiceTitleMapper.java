package cn.iocoder.yudao.module.trade.dal.mysql.invoice;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.invoice.TradeInvoiceTitleDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TradeInvoiceTitleMapper extends BaseMapperX<TradeInvoiceTitleDO> {

    /**
     * 查询某会员的全部抬头，**默认的那条排在最前**（结算页要拿它做预选）
     */
    default List<TradeInvoiceTitleDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<TradeInvoiceTitleDO>()
                .eq(TradeInvoiceTitleDO::getUserId, userId)
                .orderByDesc(TradeInvoiceTitleDO::getDefaultStatus)
                .orderByDesc(TradeInvoiceTitleDO::getId));
    }

    /**
     * 按 id + 会员查询 —— **所有读写都必须走它**，避免越权改到别人的抬头
     */
    default TradeInvoiceTitleDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(TradeInvoiceTitleDO::getId, id,
                TradeInvoiceTitleDO::getUserId, userId);
    }

    /**
     * 清除某会员的"默认"标记（设新默认前调用）
     *
     * ⚠️ 默认的唯一性靠**服务层**保证，所以这一步必须与"设置新默认"在同一个事务里，
     * 否则中途失败会留下"一条默认都没有"的状态。
     */
    default int clearDefaultByUserId(Long userId) {
        return update(new LambdaUpdateWrapper<TradeInvoiceTitleDO>()
                .eq(TradeInvoiceTitleDO::getUserId, userId)
                .eq(TradeInvoiceTitleDO::getDefaultStatus, true)
                .set(TradeInvoiceTitleDO::getDefaultStatus, false));
    }

}
