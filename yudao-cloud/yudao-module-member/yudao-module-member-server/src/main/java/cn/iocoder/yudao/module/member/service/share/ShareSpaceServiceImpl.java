package cn.iocoder.yudao.module.member.service.share;

import cn.iocoder.yudao.module.member.controller.app.share.vo.ShareSpaceOverviewRespVO;
import cn.iocoder.yudao.module.member.controller.app.subscription.vo.AppMemberSubscriptionRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgMemberDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgSeatDO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareSpaceDO;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareContentMapper;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareSpaceMapper;
import cn.iocoder.yudao.module.member.enums.org.MaxLabelOrgRoleEnum;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgLicenseService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgMemberService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgSeatService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgService;
import cn.iocoder.yudao.module.member.service.share.dto.ShareEnterpriseAccess;
import cn.iocoder.yudao.module.member.service.subscription.MemberSubscriptionService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

@Service
@Validated
public class ShareSpaceServiceImpl implements ShareSpaceService {

    private static final String EDITION_PROFESSIONAL = "professional";
    private static final String EDITION_ENTERPRISE = "enterprise";

    @Resource
    private MemberSubscriptionService subscriptionService;
    @Resource
    private MaxLabelOrgMemberService orgMemberService;
    @Resource
    private MaxLabelOrgService orgService;
    @Resource
    private MaxLabelOrgSeatService seatService;
    @Resource
    private MaxLabelOrgLicenseService licenseService;
    @Resource
    private MaxLabelShareSpaceMapper spaceMapper;
    @Resource
    private MaxLabelShareContentMapper contentMapper;

    @Override
    public ShareSpaceOverviewRespVO getOverview(Long memberId) {
        ShareSpaceOverviewRespVO resp = new ShareSpaceOverviewRespVO();
        resp.setPersonal(buildPersonal(memberId));
        resp.setEnterprise(buildEnterprise(memberId));
        return resp;
    }

    private ShareSpaceOverviewRespVO.Personal buildPersonal(Long memberId) {
        String edition = currentEdition(memberId);
        long quota = quotaForEdition(edition);
        long used = contentMapper.sumSizeByPersonal(memberId);
        ShareSpaceOverviewRespVO.Personal personal = new ShareSpaceOverviewRespVO.Personal();
        personal.setEdition(edition);
        personal.setQuotaBytes(quota);
        personal.setUsedBytes(used);
        personal.setRemainingBytes(Math.max(0, quota - used));
        personal.setCanUpload(used < quota);
        return personal;
    }

    private ShareSpaceOverviewRespVO.Enterprise buildEnterprise(Long memberId) {
        ShareEnterpriseAccess access = getEnterpriseAccess(memberId);
        if (access == null) {
            return null;
        }
        long quota = ShareConstants.ENTERPRISE_BYTES;
        long used = contentMapper.sumSizeByOrg(access.getOrgId());
        ShareSpaceOverviewRespVO.Enterprise enterprise = new ShareSpaceOverviewRespVO.Enterprise();
        enterprise.setOrgId(access.getOrgId());
        enterprise.setOrgName(access.getOrgName());
        enterprise.setEdition(EDITION_ENTERPRISE);
        enterprise.setSeatActive(access.getSeatActive());
        enterprise.setLicenseActive(access.getLicenseActive());
        enterprise.setIsAdmin(access.getIsAdmin());
        enterprise.setQuotaBytes(quota);
        enterprise.setUsedBytes(used);
        enterprise.setRemainingBytes(Math.max(0, quota - used));
        enterprise.setCanUpload(access.isWritable() && used < quota);
        enterprise.setReadOnly(access.isReadOnly());
        return enterprise;
    }

    @Override
    public ShareEnterpriseAccess getEnterpriseAccess(Long memberId) {
        MaxLabelOrgMemberDO relation = orgMemberService.getByMemberId(memberId);
        if (relation == null) {
            return null;
        }
        MaxLabelOrgDO org = orgService.getOrg(relation.getOrgId());
        if (org == null) {
            return null;
        }
        MaxLabelOrgSeatDO seat = seatService.getActiveByMemberId(memberId);
        boolean seatActive = seat != null && Objects.equals(seat.getOrgId(), org.getId());
        boolean licenseActive = Objects.equals(org.getStatus(), 0)
                && !licenseService.getUnexpiredList(org.getId()).isEmpty();
        boolean isAdmin = MaxLabelOrgRoleEnum.OWNER.getRole().equals(relation.getRole())
                || MaxLabelOrgRoleEnum.ADMIN.getRole().equals(relation.getRole());
        ShareEnterpriseAccess access = new ShareEnterpriseAccess();
        access.setOrgId(org.getId());
        access.setOrgName(org.getName());
        access.setOrgStatus(org.getStatus());
        access.setSeatActive(seatActive);
        access.setLicenseActive(licenseActive);
        access.setIsAdmin(isAdmin);
        return access;
    }

    @Override
    public long getPersonalQuotaBytes(Long memberId) {
        return quotaForEdition(currentEdition(memberId));
    }

    @Override
    public long getPersonalUsedBytes(Long memberId) {
        return contentMapper.sumSizeByPersonal(memberId);
    }

    @Override
    public long getEnterpriseUsedBytes(Long orgId) {
        return contentMapper.sumSizeByOrg(orgId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockSpace(Integer scope, Long ownerId) {
        ensureSpaceLocked(scope, ownerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshUsedBytes(Integer scope, Long ownerId) {
        MaxLabelShareSpaceDO space = ensureSpaceLocked(scope, ownerId);
        long used = ShareConstants.SCOPE_PERSONAL == scope
                ? contentMapper.sumSizeByPersonal(ownerId)
                : contentMapper.sumSizeByOrg(ownerId);
        spaceMapper.updateById(new MaxLabelShareSpaceDO().setId(space.getId()).setUsedBytes(used));
    }

    private MaxLabelShareSpaceDO ensureSpaceLocked(Integer scope, Long ownerId) {
        MaxLabelShareSpaceDO space = spaceMapper.selectForUpdate(scope, ownerId);
        if (space != null) {
            return space;
        }
        long used = ShareConstants.SCOPE_PERSONAL == scope
                ? contentMapper.sumSizeByPersonal(ownerId)
                : contentMapper.sumSizeByOrg(ownerId);
        MaxLabelShareSpaceDO created = MaxLabelShareSpaceDO.builder()
                .scope(scope).ownerId(ownerId).usedBytes(used).build();
        try {
            spaceMapper.insert(created);
        } catch (DuplicateKeyException ignored) {
            // 并发创建：唯一键兜底，下面重新加锁读取
        }
        MaxLabelShareSpaceDO locked = spaceMapper.selectForUpdate(scope, ownerId);
        return locked != null ? locked : created;
    }

    private String currentEdition(Long memberId) {
        AppMemberSubscriptionRespVO subscription = subscriptionService.getCurrent(memberId);
        String edition = subscription == null ? null : subscription.getEdition();
        return edition == null ? "standard" : edition;
    }

    private long quotaForEdition(String edition) {
        return EDITION_PROFESSIONAL.equals(edition) || EDITION_ENTERPRISE.equals(edition)
                ? ShareConstants.PERSONAL_PAID_BYTES
                : ShareConstants.PERSONAL_STANDARD_BYTES;
    }

}
