package cn.iocoder.yudao.module.member.service.org;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgMemberDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgMapper;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgMemberMapper;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgSeatMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.enums.org.MaxLabelOrgRoleEnum;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.member.enums.LogRecordConstants.*;

/**
 * MaxLabel 企业成员 Service 实现类
 *
 * 成员关系退出/移出为物理删除；席位在同一事务内释放。
 */
@Service
@Validated
public class MaxLabelOrgMemberServiceImpl implements MaxLabelOrgMemberService {

    private static final int OPERATOR_MEMBER = 1;
    private static final int OPERATOR_SYSTEM = 2;

    @Resource
    private MaxLabelOrgMapper orgMapper;
    @Resource
    private MaxLabelOrgMemberMapper orgMemberMapper;
    @Resource
    private MaxLabelOrgSeatMapper orgSeatMapper;
    @Resource
    private MemberUserMapper memberUserMapper;
    @Resource
    private MaxLabelOrgService orgService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMember(Long orgId, String mobile, Integer operatorType, Long operatorId) {
        MaxLabelOrgDO org = orgService.lockOrg(orgId);
        validateOperator(org.getId(), operatorType, operatorId, false);
        if (StrUtil.isBlank(mobile)) {
            throw exception(MEMBER_ORG_MOBILE_NOT_REGISTERED);
        }
        MemberUserDO member = memberUserMapper.selectByMobile(mobile.trim());
        if (member == null) {
            throw exception(MEMBER_ORG_MOBILE_NOT_REGISTERED);
        }
        // 锁会员行：并发加入两个不同企业时串行化，避免后到的请求报数据库异常（FR-290）
        memberUserMapper.selectByIdForUpdate(member.getId());
        MaxLabelOrgMemberDO exists = orgMemberMapper.selectByMemberId(member.getId());
        if (exists != null) {
            if (Objects.equals(exists.getOrgId(), orgId)) {
                throw exception(MEMBER_ORG_MEMBER_ALREADY);
            }
            throw exception(MEMBER_ORG_MEMBER_IN_OTHER);
        }
        try {
            orgMemberMapper.insert(MaxLabelOrgMemberDO.builder()
                    .orgId(orgId)
                    .memberId(member.getId())
                    .role(MaxLabelOrgRoleEnum.MEMBER.getRole())
                    .joinedTime(LocalDateTime.now())
                    .build());
        } catch (DuplicateKeyException e) {
            // 唯一键 uk_member(tenant_id, member_id) 兜底：不把数据库异常抛给前台
            throw exception(MEMBER_ORG_MEMBER_IN_OTHER);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long orgId, Long memberId, Integer operatorType, Long operatorId) {
        orgService.lockOrg(orgId);
        validateOperator(orgId, operatorType, operatorId, false);
        MaxLabelOrgMemberDO relation = orgMemberMapper.selectByOrgIdAndMemberId(orgId, memberId);
        if (relation == null) {
            throw exception(MEMBER_ORG_NOT_MEMBER);
        }
        if (MaxLabelOrgRoleEnum.OWNER.getRole().equals(relation.getRole())) {
            throw exception(MEMBER_ORG_OWNER_REQUIRED);
        }
        orgMemberMapper.deleteByOrgIdAndMemberId(orgId, memberId);
        revokeSeat(orgId, memberId, operatorType, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = MEMBER_ORG_TYPE, subType = MEMBER_ORG_UPDATE_ROLE_SUB_TYPE, bizNo = "{{#orgId}}",
            success = MEMBER_ORG_UPDATE_ROLE_SUCCESS)
    public void updateRole(Long orgId, Long memberId, Integer role, Integer operatorType, Long operatorId) {
        orgService.lockOrg(orgId);
        validateOperator(orgId, operatorType, operatorId, true);
        MaxLabelOrgMemberDO relation = orgMemberMapper.selectByOrgIdAndMemberId(orgId, memberId);
        if (relation == null) {
            throw exception(MEMBER_ORG_NOT_MEMBER);
        }
        if (MaxLabelOrgRoleEnum.OWNER.getRole().equals(relation.getRole())) {
            throw exception(MEMBER_ORG_OWNER_REQUIRED);
        }
        if (!MaxLabelOrgRoleEnum.ADMIN.getRole().equals(role)
                && !MaxLabelOrgRoleEnum.MEMBER.getRole().equals(role)) {
            throw exception(MEMBER_ORG_OWNER_REQUIRED);
        }
        orgMemberMapper.updateById(new MaxLabelOrgMemberDO().setId(relation.getId()).setRole(role));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = MEMBER_ORG_TYPE, subType = MEMBER_ORG_TRANSFER_OWNER_SUB_TYPE, bizNo = "{{#orgId}}",
            success = MEMBER_ORG_TRANSFER_OWNER_SUCCESS)
    public void transferOwner(Long orgId, Long targetMemberId, Integer operatorType, Long operatorId) {
        MaxLabelOrgDO org = orgService.lockOrg(orgId);
        validateOperator(orgId, operatorType, operatorId, true);
        Long currentOwnerId = org.getOwnerMemberId();
        if (Objects.equals(currentOwnerId, targetMemberId)) {
            throw exception(MEMBER_ORG_OWNER_REQUIRED);
        }
        MaxLabelOrgMemberDO target = orgMemberMapper.selectByOrgIdAndMemberId(orgId, targetMemberId);
        if (target == null) {
            throw exception(MEMBER_ORG_NOT_MEMBER);
        }
        MaxLabelOrgMemberDO oldOwner = orgMemberMapper.selectByOrgIdAndMemberId(orgId, currentOwnerId);
        if (oldOwner == null) {
            throw exception(MEMBER_ORG_OWNER_REQUIRED);
        }
        orgMemberMapper.updateById(new MaxLabelOrgMemberDO().setId(target.getId())
                .setRole(MaxLabelOrgRoleEnum.OWNER.getRole()));
        orgMemberMapper.updateById(new MaxLabelOrgMemberDO().setId(oldOwner.getId())
                .setRole(MaxLabelOrgRoleEnum.ADMIN.getRole()));
        orgMapper.updateById(new MaxLabelOrgDO().setId(org.getId()).setOwnerMemberId(targetMemberId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leave(Long memberId) {
        MaxLabelOrgMemberDO relation = orgMemberMapper.selectByMemberId(memberId);
        if (relation == null) {
            throw exception(MEMBER_ORG_NOT_MEMBER);
        }
        if (MaxLabelOrgRoleEnum.OWNER.getRole().equals(relation.getRole())) {
            throw exception(MEMBER_ORG_OWNER_CANNOT_LEAVE);
        }
        orgMemberMapper.deleteByOrgIdAndMemberId(relation.getOrgId(), memberId);
        revokeSeat(relation.getOrgId(), memberId, OPERATOR_MEMBER, memberId);
    }

    @Override
    public PageResult<MaxLabelOrgMemberDO> getMemberPage(Long orgId, String keyword, PageParam pageParam) {
        Set<Long> memberIds = null;
        if (StrUtil.isNotBlank(keyword)) {
            Set<Long> ids = new LinkedHashSet<>();
            MemberUserDO byMobile = memberUserMapper.selectByMobile(keyword.trim());
            if (byMobile != null) {
                ids.add(byMobile.getId());
            }
            List<MemberUserDO> byNickname = memberUserMapper.selectListByNicknameLike(keyword.trim());
            if (CollUtil.isNotEmpty(byNickname)) {
                byNickname.forEach(user -> ids.add(user.getId()));
            }
            memberIds = ids;
        }
        return orgMemberMapper.selectPageByOrgId(orgId, memberIds, pageParam);
    }

    @Override
    public MaxLabelOrgMemberDO getByMemberId(Long memberId) {
        return orgMemberMapper.selectByMemberId(memberId);
    }

    @Override
    public int getMemberCount(Long orgId) {
        return orgMemberMapper.selectCountByOrgId(orgId);
    }

    @Override
    public void validateOperator(Long orgId, Integer operatorType, Long operatorId, boolean ownerOnly) {
        // 系统用户由 @PreAuthorize 把关，这里不再查企业角色
        if (Objects.equals(operatorType, OPERATOR_SYSTEM)) {
            return;
        }
        MaxLabelOrgMemberDO relation = orgMemberMapper.selectByOrgIdAndMemberId(orgId, operatorId);
        if (relation == null) {
            throw exception(MEMBER_ORG_NOT_ADMIN);
        }
        if (ownerOnly) {
            if (!MaxLabelOrgRoleEnum.OWNER.getRole().equals(relation.getRole())) {
                throw exception(MEMBER_ORG_NOT_ADMIN);
            }
        } else if (!MaxLabelOrgRoleEnum.isAdmin(relation.getRole())) {
            throw exception(MEMBER_ORG_NOT_ADMIN);
        }
    }

    private void revokeSeat(Long orgId, Long memberId, Integer operatorType, Long operatorId) {
        orgSeatMapper.updateRevokedByOrgIdAndMemberId(orgId, memberId, operatorType,
                operatorId, LocalDateTime.now());
    }

}
