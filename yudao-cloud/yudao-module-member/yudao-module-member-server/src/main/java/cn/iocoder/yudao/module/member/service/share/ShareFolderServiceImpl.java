package cn.iocoder.yudao.module.member.service.share;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgMemberDO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareFolderDO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareFolderMemberDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareContentMapper;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareFolderMapper;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareFolderMemberMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.enums.org.MaxLabelOrgRoleEnum;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgMemberService;
import cn.iocoder.yudao.module.member.service.share.dto.ShareEnterpriseAccess;
import cn.iocoder.yudao.module.member.service.share.dto.ShareFolderInfo;
import cn.iocoder.yudao.module.member.service.share.dto.ShareFolderMemberInfo;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.member.enums.LogRecordConstants.*;

@Service
@Validated
public class ShareFolderServiceImpl implements ShareFolderService {

    @Resource
    private ShareSpaceService spaceService;
    @Resource
    private MaxLabelOrgMemberService orgMemberService;
    @Resource
    private MaxLabelShareFolderMapper folderMapper;
    @Resource
    private MaxLabelShareFolderMemberMapper folderMemberMapper;
    @Resource
    private MaxLabelShareContentMapper contentMapper;
    @Resource
    private MemberUserMapper memberUserMapper;

    @Override
    public List<ShareFolderInfo> getVisibleList(Long memberId) {
        ShareEnterpriseAccess access = requireSeat(memberId);
        List<MaxLabelShareFolderDO> folders = folderMapper.selectListByOrgId(access.getOrgId());
        if (!Boolean.TRUE.equals(access.getIsAdmin())) {
            List<Long> visibleIds = folderMemberMapper.selectVisibleFolderIds(memberId,
                    folders.stream().map(MaxLabelShareFolderDO::getId).toList());
            Set<Long> visibleSet = new LinkedHashSet<>(visibleIds);
            folders = folders.stream().filter(folder -> visibleSet.contains(folder.getId())).toList();
        }
        boolean admin = Boolean.TRUE.equals(access.getIsAdmin());
        List<ShareFolderInfo> result = new ArrayList<>(folders.size());
        for (MaxLabelShareFolderDO folder : folders) {
            ShareFolderInfo info = new ShareFolderInfo();
            info.setId(folder.getId());
            info.setName(folder.getName());
            info.setMemberCount(folderMemberMapper.selectMemberIdsByFolderId(folder.getId()).size());
            info.setCanManage(admin);
            if (admin) {
                info.setContentCount(contentMapper.countByFolder(folder.getId()));
                info.setUsedBytes(contentMapper.sumSizeByFolder(folder.getId()));
            }
            result.add(info);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = MEMBER_SHARE_TYPE, subType = MEMBER_SHARE_FOLDER_CREATE_SUB_TYPE,
            bizNo = "{{#memberId}}", success = MEMBER_SHARE_FOLDER_CREATE_SUCCESS)
    public Long create(Long memberId, String name) {
        ShareEnterpriseAccess access = requireAdminWithLicense(memberId);
        String folderName = validateName(name);
        MaxLabelShareFolderDO folder = MaxLabelShareFolderDO.builder()
                .orgId(access.getOrgId())
                .name(folderName)
                .creatorMemberId(memberId)
                .build();
        folderMapper.insert(folder);
        return folder.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = MEMBER_SHARE_TYPE, subType = MEMBER_SHARE_FOLDER_UPDATE_SUB_TYPE,
            bizNo = "{{#id}}", success = MEMBER_SHARE_FOLDER_UPDATE_SUCCESS)
    public void update(Long memberId, Long id, String name) {
        ShareEnterpriseAccess access = requireAdminWithLicense(memberId);
        MaxLabelShareFolderDO folder = getFolder(access.getOrgId(), id);
        folderMapper.updateById(new MaxLabelShareFolderDO().setId(folder.getId()).setName(validateName(name)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = MEMBER_SHARE_TYPE, subType = MEMBER_SHARE_FOLDER_DELETE_SUB_TYPE,
            bizNo = "{{#id}}", success = MEMBER_SHARE_FOLDER_DELETE_SUCCESS)
    public void delete(Long memberId, Long id) {
        ShareEnterpriseAccess access = requireAdminWithLicense(memberId);
        MaxLabelShareFolderDO folder = getFolder(access.getOrgId(), id);
        if (contentMapper.countByFolder(folder.getId()) > 0) {
            throw exception(MEMBER_SHARE_FOLDER_NOT_EMPTY);
        }
        folderMemberMapper.deleteByFolderId(folder.getId());
        folderMapper.deleteById(folder.getId());
    }

    @Override
    public List<ShareFolderMemberInfo> getMemberList(Long memberId, Long folderId) {
        ShareEnterpriseAccess access = requireSeat(memberId);
        if (!Boolean.TRUE.equals(access.getIsAdmin())) {
            throw exception(MEMBER_ORG_NOT_ADMIN);
        }
        MaxLabelShareFolderDO folder = getFolder(access.getOrgId(), folderId);
        Set<Long> visibleIds = new LinkedHashSet<>(folderMemberMapper.selectMemberIdsByFolderId(folder.getId()));
        List<MaxLabelOrgMemberDO> relations = orgMemberService.getMemberList(access.getOrgId());
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, MemberUserDO> users = memberUserMapper.selectBatchIds(
                        relations.stream().map(MaxLabelOrgMemberDO::getMemberId).toList())
                .stream().collect(Collectors.toMap(MemberUserDO::getId, Function.identity()));
        List<ShareFolderMemberInfo> result = new ArrayList<>(relations.size());
        for (MaxLabelOrgMemberDO relation : relations) {
            MemberUserDO user = users.get(relation.getMemberId());
            ShareFolderMemberInfo info = new ShareFolderMemberInfo();
            info.setMemberId(relation.getMemberId());
            info.setNickname(user == null ? "匿名会员" : user.getNickname());
            info.setMobile(maskMobile(user == null ? null : user.getMobile()));
            info.setRole(relation.getRole());
            info.setVisible(visibleIds.contains(relation.getMemberId()));
            result.add(info);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = MEMBER_SHARE_TYPE, subType = MEMBER_SHARE_FOLDER_MEMBERS_SUB_TYPE,
            bizNo = "{{#folderId}}", success = MEMBER_SHARE_FOLDER_MEMBERS_SUCCESS)
    public void updateMembers(Long memberId, Long folderId, List<Long> memberIds) {
        ShareEnterpriseAccess access = requireAdminWithLicense(memberId);
        MaxLabelShareFolderDO folder = getFolder(access.getOrgId(), folderId);
        Set<Long> orgMemberIds = orgMemberService.getMemberList(access.getOrgId()).stream()
                .map(MaxLabelOrgMemberDO::getMemberId).collect(Collectors.toSet());
        Set<Long> targetIds = memberIds == null ? Collections.emptySet() : new LinkedHashSet<>(memberIds);
        if (!orgMemberIds.containsAll(targetIds)) {
            throw exception(MEMBER_SHARE_FOLDER_MEMBER_INVALID);
        }
        folderMemberMapper.deleteByFolderId(folder.getId());
        for (Long targetId : targetIds) {
            folderMemberMapper.insert(MaxLabelShareFolderMemberDO.builder()
                    .folderId(folder.getId()).memberId(targetId).build());
        }
    }

    @Override
    public ShareEnterpriseAccess requireSeat(Long memberId) {
        ShareEnterpriseAccess access = spaceService.getEnterpriseAccess(memberId);
        if (access == null || !Boolean.TRUE.equals(access.getSeatActive())) {
            throw exception(MEMBER_SHARE_SEAT_REQUIRED);
        }
        return access;
    }

    @Override
    public void requireView(Long memberId, Long orgId, Long folderId) {
        ShareEnterpriseAccess access = requireSeat(memberId);
        if (!Objects.equals(access.getOrgId(), orgId) || folderId == null) {
            throw exception(MEMBER_SHARE_FOLDER_NOT_FOUND);
        }
        MaxLabelShareFolderDO folder = folderMapper.selectByIdAndOrgId(folderId, orgId);
        if (folder == null) {
            throw exception(MEMBER_SHARE_FOLDER_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(access.getIsAdmin())
                && !folderMemberMapper.existsFolderMember(folder.getId(), memberId)) {
            throw exception(MEMBER_SHARE_FOLDER_NOT_FOUND);
        }
    }

    @Override
    public MaxLabelShareFolderDO getFolder(Long orgId, Long folderId) {
        MaxLabelShareFolderDO folder = folderMapper.selectByIdAndOrgId(folderId, orgId);
        if (folder == null) {
            throw exception(MEMBER_SHARE_FOLDER_NOT_FOUND);
        }
        return folder;
    }

    private ShareEnterpriseAccess requireAdminWithLicense(Long memberId) {
        ShareEnterpriseAccess access = requireSeat(memberId);
        if (!Boolean.TRUE.equals(access.getIsAdmin())) {
            throw exception(MEMBER_ORG_NOT_ADMIN);
        }
        if (!Boolean.TRUE.equals(access.getLicenseActive())) {
            throw exception(MEMBER_SHARE_ENTERPRISE_READ_ONLY);
        }
        return access;
    }

    private String validateName(String name) {
        String value = StrUtil.trim(name);
        if (StrUtil.isBlank(value) || value.length() > 64) {
            throw exception(MEMBER_SHARE_CONTENT_INVALID);
        }
        return value;
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

}
