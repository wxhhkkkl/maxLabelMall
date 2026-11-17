package cn.iocoder.yudao.module.trade.dal.mysql.customerservice;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairOrderDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface CustomerServiceRepairOrderMapper extends BaseMapperX<CustomerServiceRepairOrderDO> {

    default CustomerServiceRepairOrderDO selectByRepairRequestId(Long repairRequestId) {
        return selectOne(new LambdaQueryWrapperX<CustomerServiceRepairOrderDO>()
                .eq(CustomerServiceRepairOrderDO::getRepairRequestId, repairRequestId)
                .last("LIMIT 1"));
    }

    default List<CustomerServiceRepairOrderDO> selectListByRepairRequestIds(Collection<Long> repairRequestIds) {
        return selectList(new LambdaQueryWrapperX<CustomerServiceRepairOrderDO>()
                .in(CustomerServiceRepairOrderDO::getRepairRequestId, repairRequestIds));
    }

    default PageResult<CustomerServiceRepairOrderDO> selectPage(Long id, Long memberId, Integer payStatus,
                                                                Integer refundStatus, LocalDateTime[] createTime,
                                                                PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<CustomerServiceRepairOrderDO>()
                .eqIfPresent(CustomerServiceRepairOrderDO::getId, id)
                .eqIfPresent(CustomerServiceRepairOrderDO::getMemberId, memberId)
                .eqIfPresent(CustomerServiceRepairOrderDO::getPayStatus, payStatus)
                .eqIfPresent(CustomerServiceRepairOrderDO::getRefundStatus, refundStatus)
                .betweenIfPresent(CustomerServiceRepairOrderDO::getCreateTime, createTime)
                .orderByDesc(CustomerServiceRepairOrderDO::getId));
    }

}
