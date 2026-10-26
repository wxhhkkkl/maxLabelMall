package cn.iocoder.yudao.module.member.dal.mysql.org;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgSeatDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MaxLabelOrgSeatMapper extends BaseMapperX<MaxLabelOrgSeatDO> {

    default MaxLabelOrgSeatDO selectByOrgIdAndMemberId(Long orgId, Long memberId) {
        return selectOne(new LambdaQueryWrapperX<MaxLabelOrgSeatDO>()
                .eq(MaxLabelOrgSeatDO::getOrgId, orgId)
                .eq(MaxLabelOrgSeatDO::getMemberId, memberId));
    }

    default MaxLabelOrgSeatDO selectActiveByMemberId(Long memberId) {
        return selectOne(new LambdaQueryWrapperX<MaxLabelOrgSeatDO>()
                .eq(MaxLabelOrgSeatDO::getMemberId, memberId)
                .eq(MaxLabelOrgSeatDO::getStatus, 0));
    }

    default List<MaxLabelOrgSeatDO> selectActiveListByOrgId(Long orgId) {
        return selectList(new LambdaQueryWrapperX<MaxLabelOrgSeatDO>()
                .eq(MaxLabelOrgSeatDO::getOrgId, orgId)
                .eq(MaxLabelOrgSeatDO::getStatus, 0));
    }

    default int selectActiveCountByOrgId(Long orgId) {
        return selectCount(new LambdaQueryWrapperX<MaxLabelOrgSeatDO>()
                .eq(MaxLabelOrgSeatDO::getOrgId, orgId)
                .eq(MaxLabelOrgSeatDO::getStatus, 0)).intValue();
    }

    default PageResult<MaxLabelOrgSeatDO> selectPageByOrgId(Long orgId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<MaxLabelOrgSeatDO>()
                .eq(MaxLabelOrgSeatDO::getOrgId, orgId)
                .orderByDesc(MaxLabelOrgSeatDO::getId));
    }

    default int updateRevokedByOrgIdAndMemberId(Long orgId, Long memberId,
                                                Integer operatorType, Long operatorId,
                                                java.time.LocalDateTime revokedTime) {
        return update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MaxLabelOrgSeatDO>()
                .eq(MaxLabelOrgSeatDO::getOrgId, orgId)
                .eq(MaxLabelOrgSeatDO::getMemberId, memberId)
                .eq(MaxLabelOrgSeatDO::getStatus, 0)
                .set(MaxLabelOrgSeatDO::getStatus, 1)
                .set(MaxLabelOrgSeatDO::getRevokedTime, revokedTime)
                .set(MaxLabelOrgSeatDO::getOperatorType, operatorType)
                .set(MaxLabelOrgSeatDO::getOperatorId, operatorId));
    }

}
