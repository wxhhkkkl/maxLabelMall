package cn.iocoder.yudao.module.member.dal.mysql.share;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareFolderDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

import static cn.iocoder.yudao.module.member.service.share.ShareConstants.SCOPE_ENTERPRISE;
import static cn.iocoder.yudao.module.member.service.share.ShareConstants.SCOPE_PERSONAL;

@Mapper
public interface MaxLabelShareFolderMapper extends BaseMapperX<MaxLabelShareFolderDO> {

    default List<MaxLabelShareFolderDO> selectListByOrgId(Long orgId) {
        return selectList(new LambdaQueryWrapperX<MaxLabelShareFolderDO>()
                .eq(MaxLabelShareFolderDO::getScope, SCOPE_ENTERPRISE)
                .eq(MaxLabelShareFolderDO::getOrgId, orgId)
                .orderByAsc(MaxLabelShareFolderDO::getId));
    }

    default MaxLabelShareFolderDO selectByIdAndOrgId(Long id, Long orgId) {
        return selectOne(new LambdaQueryWrapperX<MaxLabelShareFolderDO>()
                .eq(MaxLabelShareFolderDO::getId, id)
                .eq(MaxLabelShareFolderDO::getScope, SCOPE_ENTERPRISE)
                .eq(MaxLabelShareFolderDO::getOrgId, orgId));
    }

    default int deleteByIdAndOrgId(Long id, Long orgId) {
        return delete(new LambdaQueryWrapperX<MaxLabelShareFolderDO>()
                .eq(MaxLabelShareFolderDO::getId, id)
                .eq(MaxLabelShareFolderDO::getScope, SCOPE_ENTERPRISE)
                .eq(MaxLabelShareFolderDO::getOrgId, orgId));
    }

    // ======================= 个人空间文件夹 =======================

    default List<MaxLabelShareFolderDO> selectPersonalList(Long ownerMemberId) {
        return selectList(new LambdaQueryWrapperX<MaxLabelShareFolderDO>()
                .eq(MaxLabelShareFolderDO::getScope, SCOPE_PERSONAL)
                .eq(MaxLabelShareFolderDO::getOwnerMemberId, ownerMemberId)
                .orderByAsc(MaxLabelShareFolderDO::getParentId)
                .orderByAsc(MaxLabelShareFolderDO::getId));
    }

    default MaxLabelShareFolderDO selectPersonalById(Long ownerMemberId, Long id) {
        return selectOne(new LambdaQueryWrapperX<MaxLabelShareFolderDO>()
                .eq(MaxLabelShareFolderDO::getId, id)
                .eq(MaxLabelShareFolderDO::getScope, SCOPE_PERSONAL)
                .eq(MaxLabelShareFolderDO::getOwnerMemberId, ownerMemberId));
    }

    /** 移动个人文件夹；显式 set 以支持移动到根目录（parentId=null） */
    default int updatePersonalParent(Long ownerMemberId, Long id, Long parentId) {
        return update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MaxLabelShareFolderDO>()
                .eq(MaxLabelShareFolderDO::getId, id)
                .eq(MaxLabelShareFolderDO::getScope, SCOPE_PERSONAL)
                .eq(MaxLabelShareFolderDO::getOwnerMemberId, ownerMemberId)
                .set(MaxLabelShareFolderDO::getParentId, parentId));
    }

}
