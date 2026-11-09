package cn.iocoder.yudao.module.member.dal.mysql.share;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareFolderDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MaxLabelShareFolderMapper extends BaseMapperX<MaxLabelShareFolderDO> {

    default List<MaxLabelShareFolderDO> selectListByOrgId(Long orgId) {
        return selectList(new LambdaQueryWrapperX<MaxLabelShareFolderDO>()
                .eq(MaxLabelShareFolderDO::getOrgId, orgId)
                .orderByAsc(MaxLabelShareFolderDO::getId));
    }

    default MaxLabelShareFolderDO selectByIdAndOrgId(Long id, Long orgId) {
        return selectOne(new LambdaQueryWrapperX<MaxLabelShareFolderDO>()
                .eq(MaxLabelShareFolderDO::getId, id)
                .eq(MaxLabelShareFolderDO::getOrgId, orgId));
    }

    default int deleteByIdAndOrgId(Long id, Long orgId) {
        return delete(new LambdaQueryWrapperX<MaxLabelShareFolderDO>()
                .eq(MaxLabelShareFolderDO::getId, id)
                .eq(MaxLabelShareFolderDO::getOrgId, orgId));
    }

}
