package cn.iocoder.yudao.module.trade.dal.mysql.customization;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationOrderDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CustomizationOrderMapper extends BaseMapperX<CustomizationOrderDO> {

    default CustomizationOrderDO selectByOrderId(Long orderId) {
        return selectOne(CustomizationOrderDO::getOrderId, orderId);
    }

    default List<CustomizationOrderDO> selectListByOrderIds(Collection<Long> orderIds) {
        return selectList(new LambdaQueryWrapperX<CustomizationOrderDO>()
                .in(CustomizationOrderDO::getOrderId, orderIds));
    }

}
