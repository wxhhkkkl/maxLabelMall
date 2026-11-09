package cn.iocoder.yudao.module.member.dal.mysql.share;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareSpaceDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MaxLabelShareSpaceMapper extends BaseMapperX<MaxLabelShareSpaceDO> {

    default MaxLabelShareSpaceDO selectByScopeAndOwner(Integer scope, Long ownerId) {
        return selectOne(new LambdaQueryWrapperX<MaxLabelShareSpaceDO>()
                .eq(MaxLabelShareSpaceDO::getScope, scope)
                .eq(MaxLabelShareSpaceDO::getOwnerId, ownerId));
    }

    /**
     * 锁住空间行，用于上传/删除时的容量串行化。
     */
    default MaxLabelShareSpaceDO selectForUpdate(Integer scope, Long ownerId) {
        return selectOne(new LambdaQueryWrapperX<MaxLabelShareSpaceDO>()
                .eq(MaxLabelShareSpaceDO::getScope, scope)
                .eq(MaxLabelShareSpaceDO::getOwnerId, ownerId)
                .last("FOR UPDATE"));
    }

}
