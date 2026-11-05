package cn.iocoder.yudao.module.member.service.share;

import cn.iocoder.yudao.module.member.controller.app.share.vo.ShareSpaceOverviewRespVO;
import cn.iocoder.yudao.module.member.controller.app.subscription.vo.AppMemberSubscriptionRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgLicenseDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgMemberDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgSeatDO;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareContentMapper;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareSpaceMapper;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgLicenseService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgMemberService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgSeatService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgService;
import cn.iocoder.yudao.module.member.service.share.dto.ShareEnterpriseAccess;
import cn.iocoder.yudao.module.member.service.subscription.MemberSubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ShareSpaceServiceImplTest {

    private static final Long MEMBER_ID = 1001L;
    private static final Long ORG_ID = 2001L;

    @Mock private MemberSubscriptionService subscriptionService;
    @Mock private MaxLabelOrgMemberService orgMemberService;
    @Mock private MaxLabelOrgService orgService;
    @Mock private MaxLabelOrgSeatService seatService;
    @Mock private MaxLabelOrgLicenseService licenseService;
    @Mock private MaxLabelShareSpaceMapper spaceMapper;
    @Mock private MaxLabelShareContentMapper contentMapper;

    @InjectMocks private ShareSpaceServiceImpl spaceService;

    @Test
    void personalStandardQuotaIsTenMegabytes() {
        when(subscriptionService.getCurrent(MEMBER_ID)).thenReturn(subscription(null));
        when(contentMapper.sumSizeByPersonal(MEMBER_ID)).thenReturn(0L);

        ShareSpaceOverviewRespVO.Personal personal = spaceService.getOverview(MEMBER_ID).getPersonal();
        assertEquals("standard", personal.getEdition());
        assertEquals(10L * 1024 * 1024, personal.getQuotaBytes());
        assertTrue(personal.getCanUpload());
    }

    @Test
    void personalProfessionalQuotaIsOneHundredMegabytes() {
        when(subscriptionService.getCurrent(MEMBER_ID)).thenReturn(subscription("professional"));
        when(contentMapper.sumSizeByPersonal(MEMBER_ID)).thenReturn(50L * 1024 * 1024);

        ShareSpaceOverviewRespVO.Personal personal = spaceService.getOverview(MEMBER_ID).getPersonal();
        assertEquals(100L * 1024 * 1024, personal.getQuotaBytes());
        assertTrue(personal.getCanUpload());
    }

    @Test
    void enterpriseRequiresSeatAndActiveLicense() {
        when(orgMemberService.getByMemberId(MEMBER_ID)).thenReturn(MaxLabelOrgMemberDO.builder()
                .orgId(ORG_ID).memberId(MEMBER_ID).role(3).build());
        when(orgService.getOrg(ORG_ID)).thenReturn(MaxLabelOrgDO.builder()
                .id(ORG_ID).name("赋签").status(0).build());
        when(seatService.getActiveByMemberId(MEMBER_ID)).thenReturn(MaxLabelOrgSeatDO.builder()
                .orgId(ORG_ID).memberId(MEMBER_ID).status(0).build());
        when(licenseService.getUnexpiredList(ORG_ID)).thenReturn(List.of(MaxLabelOrgLicenseDO.builder().build()));

        ShareEnterpriseAccess access = spaceService.getEnterpriseAccess(MEMBER_ID);
        assertTrue(access.getSeatActive());
        assertTrue(access.getLicenseActive());
        assertFalse(access.getIsAdmin());
        assertFalse(access.isReadOnly());
        assertTrue(access.isWritable());
    }

    @Test
    void enterpriseWithoutSeatIsNotWritable() {
        when(orgMemberService.getByMemberId(MEMBER_ID)).thenReturn(MaxLabelOrgMemberDO.builder()
                .orgId(ORG_ID).memberId(MEMBER_ID).role(3).build());
        when(orgService.getOrg(ORG_ID)).thenReturn(MaxLabelOrgDO.builder()
                .id(ORG_ID).name("赋签").status(0).build());
        when(seatService.getActiveByMemberId(MEMBER_ID)).thenReturn(null);
        when(licenseService.getUnexpiredList(ORG_ID)).thenReturn(List.of(MaxLabelOrgLicenseDO.builder().build()));

        ShareEnterpriseAccess access = spaceService.getEnterpriseAccess(MEMBER_ID);
        assertFalse(access.getSeatActive());
        assertFalse(access.isWritable());
    }

    @Test
    void expiredLicenseIsReadOnly() {
        when(orgMemberService.getByMemberId(MEMBER_ID)).thenReturn(MaxLabelOrgMemberDO.builder()
                .orgId(ORG_ID).memberId(MEMBER_ID).role(1).build());
        when(orgService.getOrg(ORG_ID)).thenReturn(MaxLabelOrgDO.builder()
                .id(ORG_ID).name("赋签").status(0).build());
        when(seatService.getActiveByMemberId(MEMBER_ID)).thenReturn(MaxLabelOrgSeatDO.builder()
                .orgId(ORG_ID).memberId(MEMBER_ID).status(0).build());
        when(licenseService.getUnexpiredList(ORG_ID)).thenReturn(List.of());

        ShareEnterpriseAccess access = spaceService.getEnterpriseAccess(MEMBER_ID);
        assertTrue(access.getSeatActive());
        assertFalse(access.getLicenseActive());
        assertTrue(access.getIsAdmin());
        assertTrue(access.isReadOnly());
        assertFalse(access.isWritable());
    }

    private AppMemberSubscriptionRespVO subscription(String edition) {
        AppMemberSubscriptionRespVO vo = new AppMemberSubscriptionRespVO();
        vo.setEdition(edition);
        return vo;
    }

}
