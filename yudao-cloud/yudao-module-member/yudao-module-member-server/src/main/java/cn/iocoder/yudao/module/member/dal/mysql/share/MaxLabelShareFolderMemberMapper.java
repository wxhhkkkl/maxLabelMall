package cn.iocoder.yudao.module.member.dal.mysql.share;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareFolderMemberDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MaxLabelShareFolderMemberMapper extends BaseMapperX<MaxLabelShareFolderMemberDO> {

    default List<Long> selectMemberIdsByFolderId(Long folderId) {
        return selectList(new LambdaQueryWrapperX<MaxLabelShareFolderMemberDO>()
                .eq(MaxLabelShareFolderMemberDO::getFolderId, folderId))
                .stream().map(MaxLabelShareFolderMemberDO::getMemberId).toList();
    }

    default List<Long> selectVisibleFolderIds(Long memberId, Collection<Long> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<MaxLabelShareFolderMemberDO>()
                .eq(MaxLabelShareFolderMemberDO::getMemberId, memberId)
                .in(MaxLabelShareFolderMemberDO::getFolderId, folderIds))
                .stream().map(MaxLabelShareFolderMemberDO::getFolderId).distinct().toList();
    }

    default boolean existsFolderMember(Long folderId, Long memberId) {
        return selectCount(new LambdaQueryWrapperX<MaxLabelShareFolderMemberDO>()
                .eq(MaxLabelShareFolderMemberDO::getFolderId, folderId)
                .eq(MaxLabelShareFolderMemberDO::getMemberId, memberId)) > 0;
    }

    default int deleteByFolderId(Long folderId) {
        return delete(new LambdaQueryWrapperX<MaxLabelShareFolderMemberDO>()
                .eq(MaxLabelShareFolderMemberDO::getFolderId, folderId));
    }

    default int deleteByMemberId(Long memberId) {
        return delete(new LambdaQueryWrapperX<MaxLabelShareFolderMemberDO>()
                .eq(MaxLabelShareFolderMemberDO::getMemberId, memberId));
    }

}
