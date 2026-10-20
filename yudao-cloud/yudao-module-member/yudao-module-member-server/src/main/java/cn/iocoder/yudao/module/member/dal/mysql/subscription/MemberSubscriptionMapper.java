package cn.iocoder.yudao.module.member.dal.mysql.subscription;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.subscription.MemberSubscriptionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MemberSubscriptionMapper extends BaseMapperX<MemberSubscriptionDO> {

    default MemberSubscriptionDO selectBySourceOrderItemId(Long sourceOrderItemId) {
        return selectOne(MemberSubscriptionDO::getSourceOrderItemId, sourceOrderItemId);
    }

    default List<MemberSubscriptionDO> selectListByMemberId(Long memberId) {
        return selectList(new LambdaQueryWrapperX<MemberSubscriptionDO>()
                .eq(MemberSubscriptionDO::getMemberId, memberId)
                .orderByAsc(MemberSubscriptionDO::getEndTime));
    }

    default List<MemberSubscriptionDO> selectListByMemberIds(Collection<Long> memberIds) {
        return selectList(new LambdaQueryWrapperX<MemberSubscriptionDO>()
                .in(MemberSubscriptionDO::getMemberId, memberIds)
                .orderByAsc(MemberSubscriptionDO::getEndTime));
    }

    default PageResult<MemberSubscriptionDO> selectPageByMemberId(Long memberId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<MemberSubscriptionDO>()
                .eq(MemberSubscriptionDO::getMemberId, memberId)
                .orderByDesc(MemberSubscriptionDO::getEndTime));
    }

}
