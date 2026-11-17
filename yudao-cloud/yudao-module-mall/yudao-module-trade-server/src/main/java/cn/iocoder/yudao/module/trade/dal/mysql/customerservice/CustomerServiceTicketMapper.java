package cn.iocoder.yudao.module.trade.dal.mysql.customerservice;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceTicketDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;

@Mapper
public interface CustomerServiceTicketMapper extends BaseMapperX<CustomerServiceTicketDO> {

    default Long selectCountByMemberIdAndStatus(Long memberId, Integer status) {
        return selectCount(new LambdaQueryWrapperX<CustomerServiceTicketDO>()
                .eq(CustomerServiceTicketDO::getMemberId, memberId)
                .eq(CustomerServiceTicketDO::getStatus, status));
    }

    default CustomerServiceTicketDO selectRecentSame(Long memberId, Integer type, String content,
                                                     LocalDateTime after) {
        return selectOne(new LambdaQueryWrapperX<CustomerServiceTicketDO>()
                .eq(CustomerServiceTicketDO::getMemberId, memberId)
                .eq(CustomerServiceTicketDO::getType, type)
                .eq(CustomerServiceTicketDO::getContent, content)
                .gt(CustomerServiceTicketDO::getCreateTime, after)
                .orderByDesc(CustomerServiceTicketDO::getId)
                .last("LIMIT 1"));
    }

    default PageResult<CustomerServiceTicketDO> selectPage(Long memberId, Integer type, Integer status,
                                                           Integer source, Collection<Long> memberIds,
                                                           String keyword, LocalDateTime[] createTime,
                                                           PageParam pageParam) {
        LambdaQueryWrapperX<CustomerServiceTicketDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.eqIfPresent(CustomerServiceTicketDO::getMemberId, memberId)
                .eqIfPresent(CustomerServiceTicketDO::getType, type)
                .eqIfPresent(CustomerServiceTicketDO::getStatus, status)
                .eqIfPresent(CustomerServiceTicketDO::getSource, source)
                .betweenIfPresent(CustomerServiceTicketDO::getCreateTime, createTime)
                .orderByDesc(CustomerServiceTicketDO::getId);
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> {
                if (CollUtil.isNotEmpty(memberIds)) {
                    w.in(CustomerServiceTicketDO::getMemberId, memberIds).or();
                }
                w.like(CustomerServiceTicketDO::getContent, keyword)
                        .or().like(CustomerServiceTicketDO::getContactMobile, keyword);
            });
        }
        return selectPage(pageParam, wrapper);
    }

}
