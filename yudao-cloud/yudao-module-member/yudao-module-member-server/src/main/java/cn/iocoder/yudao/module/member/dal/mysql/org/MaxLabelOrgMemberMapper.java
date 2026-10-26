package cn.iocoder.yudao.module.member.dal.mysql.org;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgMemberDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MaxLabelOrgMemberMapper extends BaseMapperX<MaxLabelOrgMemberDO> {

    default MaxLabelOrgMemberDO selectByMemberId(Long memberId) {
        return selectOne(MaxLabelOrgMemberDO::getMemberId, memberId);
    }

    default MaxLabelOrgMemberDO selectByOrgIdAndMemberId(Long orgId, Long memberId) {
        return selectOne(new LambdaQueryWrapperX<MaxLabelOrgMemberDO>()
                .eq(MaxLabelOrgMemberDO::getOrgId, orgId)
                .eq(MaxLabelOrgMemberDO::getMemberId, memberId));
    }

    default List<MaxLabelOrgMemberDO> selectListByOrgId(Long orgId) {
        return selectList(new LambdaQueryWrapperX<MaxLabelOrgMemberDO>()
                .eq(MaxLabelOrgMemberDO::getOrgId, orgId)
                .orderByAsc(MaxLabelOrgMemberDO::getId));
    }

    default List<MaxLabelOrgMemberDO> selectListByMemberIds(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<MaxLabelOrgMemberDO>()
                .in(MaxLabelOrgMemberDO::getMemberId, memberIds));
    }

    default PageResult<MaxLabelOrgMemberDO> selectPageByOrgId(Long orgId, Collection<Long> memberIds,
                                                              PageParam pageParam) {
        LambdaQueryWrapperX<MaxLabelOrgMemberDO> wrapper = new LambdaQueryWrapperX<MaxLabelOrgMemberDO>()
                .eq(MaxLabelOrgMemberDO::getOrgId, orgId);
        if (memberIds != null) {
            if (memberIds.isEmpty()) {
                return PageResult.empty();
            }
            wrapper.in(MaxLabelOrgMemberDO::getMemberId, memberIds);
        }
        return selectPage(pageParam, wrapper.orderByAsc(MaxLabelOrgMemberDO::getId));
    }

    default int deleteByOrgIdAndMemberId(Long orgId, Long memberId) {
        return delete(new LambdaQueryWrapperX<MaxLabelOrgMemberDO>()
                .eq(MaxLabelOrgMemberDO::getOrgId, orgId)
                .eq(MaxLabelOrgMemberDO::getMemberId, memberId));
    }

    default int selectCountByOrgId(Long orgId) {
        return selectCount(new LambdaQueryWrapperX<MaxLabelOrgMemberDO>()
                .eq(MaxLabelOrgMemberDO::getOrgId, orgId)).intValue();
    }

}
