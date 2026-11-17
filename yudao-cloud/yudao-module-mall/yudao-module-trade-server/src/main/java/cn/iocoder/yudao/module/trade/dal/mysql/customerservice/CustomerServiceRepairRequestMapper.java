package cn.iocoder.yudao.module.trade.dal.mysql.customerservice;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairRequestDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;

@Mapper
public interface CustomerServiceRepairRequestMapper extends BaseMapperX<CustomerServiceRepairRequestDO> {

    default Long selectCountByMemberIdAndStatusIn(Long memberId, Collection<Integer> statuses) {
        return selectCount(new LambdaQueryWrapperX<CustomerServiceRepairRequestDO>()
                .eq(CustomerServiceRepairRequestDO::getMemberId, memberId)
                .in(CustomerServiceRepairRequestDO::getStatus, statuses));
    }

    default PageResult<CustomerServiceRepairRequestDO> selectPage(Long memberId, Integer status, Integer source,
                                                                  Collection<Long> memberIds, String keyword,
                                                                  LocalDateTime[] createTime, PageParam pageParam) {
        LambdaQueryWrapperX<CustomerServiceRepairRequestDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.eqIfPresent(CustomerServiceRepairRequestDO::getMemberId, memberId)
                .eqIfPresent(CustomerServiceRepairRequestDO::getStatus, status)
                .eqIfPresent(CustomerServiceRepairRequestDO::getSource, source)
                .betweenIfPresent(CustomerServiceRepairRequestDO::getCreateTime, createTime)
                .orderByDesc(CustomerServiceRepairRequestDO::getId);
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> {
                if (CollUtil.isNotEmpty(memberIds)) {
                    w.in(CustomerServiceRepairRequestDO::getMemberId, memberIds).or();
                }
                w.like(CustomerServiceRepairRequestDO::getDeviceModel, keyword)
                        .or().like(CustomerServiceRepairRequestDO::getContactMobile, keyword);
            });
        }
        return selectPage(pageParam, wrapper);
    }

}
