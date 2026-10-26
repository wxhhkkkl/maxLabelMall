package cn.iocoder.yudao.module.member.service.org;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgMemberDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgMapper;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgMemberMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.enums.org.MaxLabelOrgRoleEnum;
import cn.iocoder.yudao.module.member.enums.org.MaxLabelOrgStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;

/**
 * MaxLabel 企业 Service 实现类
 */
@Service
@Validated
public class MaxLabelOrgServiceImpl implements MaxLabelOrgService {

    @Resource
    private MaxLabelOrgMapper orgMapper;
    @Resource
    private MaxLabelOrgMemberMapper orgMemberMapper;
    @Resource
    private MemberUserMapper memberUserMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrg(String name, Long ownerMemberId, String remark) {
        if (StrUtil.isBlank(name) || ownerMemberId == null) {
            throw exception(MEMBER_ORG_NOT_EXISTS);
        }
        MemberUserDO owner = memberUserMapper.selectById(ownerMemberId);
        if (owner == null) {
            throw exception(USER_NOT_EXISTS);
        }
        // 一人一企业：唯一键兜底，这里先给可读报错
        if (orgMemberMapper.selectByMemberId(ownerMemberId) != null) {
            throw exception(MEMBER_ORG_MEMBER_IN_OTHER);
        }
        MaxLabelOrgDO org = MaxLabelOrgDO.builder()
                .name(name.trim())
                .ownerMemberId(ownerMemberId)
                .status(MaxLabelOrgStatusEnum.ENABLE.getStatus())
                .remark(StrUtil.blankToDefault(remark, ""))
                .build();
        orgMapper.insert(org);
        orgMemberMapper.insert(MaxLabelOrgMemberDO.builder()
                .orgId(org.getId())
                .memberId(ownerMemberId)
                .role(MaxLabelOrgRoleEnum.OWNER.getRole())
                .joinedTime(LocalDateTime.now())
                .build());
        return org.getId();
    }

    @Override
    public void updateOrg(Long id, String name, String remark) {
        validateOrgExists(id);
        if (StrUtil.isBlank(name)) {
            throw exception(MEMBER_ORG_NOT_EXISTS);
        }
        orgMapper.updateById(new MaxLabelOrgDO().setId(id).setName(name.trim())
                .setRemark(StrUtil.blankToDefault(remark, "")));
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        validateOrgExists(id);
        if (MaxLabelOrgStatusEnum.valueOf(status) == null) {
            throw exception(MEMBER_ORG_NOT_EXISTS);
        }
        orgMapper.updateById(new MaxLabelOrgDO().setId(id).setStatus(status));
    }

    @Override
    public MaxLabelOrgDO getOrg(Long id) {
        return orgMapper.selectById(id);
    }

    @Override
    public MaxLabelOrgDO validateOrgEnabled(Long id) {
        MaxLabelOrgDO org = validateOrgExists(id);
        if (!MaxLabelOrgStatusEnum.ENABLE.getStatus().equals(org.getStatus())) {
            throw exception(MEMBER_ORG_DISABLED);
        }
        return org;
    }

    @Override
    public PageResult<MaxLabelOrgDO> getOrgPage(String name, Integer status, PageParam pageParam) {
        return orgMapper.selectPage(name, status, pageParam);
    }

    @Override
    public MemberOrgRespDTO getMemberOrg(Long memberId) {
        MaxLabelOrgMemberDO relation = orgMemberMapper.selectByMemberId(memberId);
        if (relation == null) {
            return null;
        }
        MaxLabelOrgDO org = orgMapper.selectById(relation.getOrgId());
        if (org == null) {
            return null;
        }
        MemberOrgRespDTO resp = new MemberOrgRespDTO();
        resp.setOrgId(org.getId());
        resp.setOrgName(org.getName());
        resp.setRole(relation.getRole());
        resp.setStatus(org.getStatus());
        return resp;
    }

    @Override
    public MemberOrgRespDTO requireOrgAdmin(Long memberId) {
        MemberOrgRespDTO org = getMemberOrg(memberId);
        if (org == null) {
            throw exception(MEMBER_ORG_NOT_ADMIN);
        }
        if (!MaxLabelOrgStatusEnum.ENABLE.getStatus().equals(org.getStatus())) {
            throw exception(MEMBER_ORG_DISABLED);
        }
        if (!MaxLabelOrgRoleEnum.isAdmin(org.getRole())) {
            throw exception(MEMBER_ORG_NOT_ADMIN);
        }
        return org;
    }

    @Override
    public MaxLabelOrgDO lockOrg(Long id) {
        MaxLabelOrgDO org = orgMapper.selectByIdForUpdate(id);
        if (org == null) {
            throw exception(MEMBER_ORG_NOT_EXISTS);
        }
        if (!MaxLabelOrgStatusEnum.ENABLE.getStatus().equals(org.getStatus())) {
            throw exception(MEMBER_ORG_DISABLED);
        }
        return org;
    }

    private MaxLabelOrgDO validateOrgExists(Long id) {
        MaxLabelOrgDO org = orgMapper.selectById(id);
        if (org == null) {
            throw exception(MEMBER_ORG_NOT_EXISTS);
        }
        return org;
    }

}
