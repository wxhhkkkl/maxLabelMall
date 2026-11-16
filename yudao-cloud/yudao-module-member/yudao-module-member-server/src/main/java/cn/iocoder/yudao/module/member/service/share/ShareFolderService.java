package cn.iocoder.yudao.module.member.service.share;

import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareFolderDO;
import cn.iocoder.yudao.module.member.service.share.dto.ShareEnterpriseAccess;
import cn.iocoder.yudao.module.member.service.share.dto.ShareFolderInfo;
import cn.iocoder.yudao.module.member.service.share.dto.ShareFolderMemberInfo;

import java.util.List;

/**
 * MaxLabel 企业文件夹 Service
 */
public interface ShareFolderService {

    List<ShareFolderInfo> getVisibleList(Long memberId);

    /** 管理端：查看企业全部文件夹与用量，不校验席位/授权 */
    List<ShareFolderInfo> getOrgListForAdmin(Long orgId);

    Long create(Long memberId, String name);

    void update(Long memberId, Long id, String name);

    void delete(Long memberId, Long id);

    List<ShareFolderMemberInfo> getMemberList(Long memberId, Long folderId);

    void updateMembers(Long memberId, Long folderId, List<Long> memberIds);

    /** 要求有未撤销席位；返回企业上下文 */
    ShareEnterpriseAccess requireSeat(Long memberId);

    /** 校验文件夹存在且当前会员可见（管理员绕过名单） */
    void requireView(Long memberId, Long orgId, Long folderId);

    MaxLabelShareFolderDO getFolder(Long orgId, Long folderId);

    // ======================= 个人空间文件夹 =======================

    List<ShareFolderInfo> getPersonalList(Long ownerMemberId);

    Long createPersonal(Long ownerMemberId, String name, Long parentId);

    void updatePersonal(Long ownerMemberId, Long id, String name);

    void deletePersonal(Long ownerMemberId, Long id);

    void movePersonal(Long ownerMemberId, Long id, Long parentId);

    /** 校验个人文件夹归属；不存在或不属于该会员时抛文件夹不存在 */
    MaxLabelShareFolderDO requirePersonalFolder(Long ownerMemberId, Long folderId);

    /** 按来源分类路径（`父/子`）创建或复用嵌套文件夹，返回最深一级编号 */
    Long resolvePersonalFolder(Long ownerMemberId, String categoryPath);

}
