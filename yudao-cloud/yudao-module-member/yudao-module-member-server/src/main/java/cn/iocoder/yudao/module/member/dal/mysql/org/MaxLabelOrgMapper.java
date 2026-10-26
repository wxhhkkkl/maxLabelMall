package cn.iocoder.yudao.module.member.dal.mysql.org;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MaxLabelOrgMapper extends BaseMapperX<MaxLabelOrgDO> {

    default PageResult<MaxLabelOrgDO> selectPage(String name, Integer status, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<MaxLabelOrgDO>()
                .likeIfPresent(MaxLabelOrgDO::getName, name)
                .eqIfPresent(MaxLabelOrgDO::getStatus, status)
                .orderByDesc(MaxLabelOrgDO::getId));
    }

    default MaxLabelOrgDO selectByOwnerMemberId(Long ownerMemberId) {
        return selectOne(MaxLabelOrgDO::getOwnerMemberId, ownerMemberId);
    }

    /** 行锁：席位分配/撤销、OWNER 转交、批次发放共用 */
    default MaxLabelOrgDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<MaxLabelOrgDO>()
                .eq(MaxLabelOrgDO::getId, id)
                .last("FOR UPDATE"));
    }

}
