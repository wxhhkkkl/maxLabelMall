package cn.iocoder.yudao.module.member.controller.admin.license;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGrantPageItemRespVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGrantPageReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.license.MemberLicenseGrantDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.license.MemberLicenseGrantMapper;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.license.LicenseGrantService;
import cn.iocoder.yudao.module.member.service.license.LicenseQueryService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 赠送记录分页（020 / 2026-11-19 线上 500 回归）
 *
 * 事故：首次赠送是个人会员，企业 id 集合为空，`selectBatchIds(空)` 拼出 `id IN ()`
 * → MySQL 语法错 → 「赠送记录」页签 500。赠送本身是成功的，坏在回填名字这一步。
 * 这里钉住「空集合不得下发 SQL」，同时保住正常回填。
 */
@ExtendWith(MockitoExtension.class)
public class LicenseDashboardControllerGrantPageTest {

    @Mock
    private LicenseGrantService licenseGrantService;
    @Mock
    private LicenseQueryService licenseQueryService;
    @Mock
    private MemberUserMapper memberUserMapper;
    @Mock
    private MaxLabelOrgMapper orgMapper;
    @Mock
    private AdminUserApi adminUserApi;
    @InjectMocks
    private LicenseDashboardController controller;

    @Test
    public void testGetGrantPage_onlyMemberGrants_doesNotQueryOrganizations() {
        MemberLicenseGrantDO grant = MemberLicenseGrantDO.builder()
                .id(1L).targetType(MemberLicenseGrantDO.TARGET_TYPE_MEMBER).targetId(639L)
                .contentType(MemberLicenseGrantDO.CONTENT_TYPE_MONTHS).edition("professional")
                .months(12).reason("维护客户")
                .afterEndTime(LocalDateTime.of(2027, 11, 20, 0, 0))
                .operatorId(249L).build();
        when(licenseGrantService.getGrantPage(any()))
                .thenReturn(new PageResult<>(List.of(grant), 1L));
        when(memberUserMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(MemberUserDO.builder().id(639L).nickname("张先生").build()));
        AdminUserRespDTO operator = new AdminUserRespDTO();
        operator.setId(249L);
        operator.setNickname("运营小王");
        when(adminUserApi.getUserList(anyCollection()))
                .thenReturn(CommonResult.success(List.of(operator)));

        CommonResult<PageResult<LicenseGrantPageItemRespVO>> result =
                controller.getGrantPage(new LicenseGrantPageReqVO());

        LicenseGrantPageItemRespVO row = result.getData().getList().get(0);
        assertEquals("张先生", row.getTargetName());
        assertEquals("运营小王", row.getOperatorName());
        // 只有个人赠送时，绝不能拿空集合去查企业（IN () 会让整页 500）
        verify(orgMapper, never()).selectBatchIds(any());
    }

    @Test
    public void testGetGrantPage_onlyOrgGrants_doesNotQueryMembers() {
        MemberLicenseGrantDO grant = MemberLicenseGrantDO.builder()
                .id(2L).targetType(MemberLicenseGrantDO.TARGET_TYPE_ORG).targetId(1L)
                .contentType(MemberLicenseGrantDO.CONTENT_TYPE_SEATS).edition("enterprise")
                .seats(5).reason("补发席位")
                .afterEndTime(LocalDateTime.of(2027, 11, 20, 0, 0))
                .operatorId(249L).build();
        when(licenseGrantService.getGrantPage(any()))
                .thenReturn(new PageResult<>(List.of(grant), 1L));
        when(orgMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO.builder()
                        .id(1L).name("示例企业").build()));
        when(adminUserApi.getUserList(anyCollection())).thenReturn(CommonResult.success(List.of()));

        CommonResult<PageResult<LicenseGrantPageItemRespVO>> result =
                controller.getGrantPage(new LicenseGrantPageReqVO());

        assertNotNull(result.getData().getList().get(0).getTargetName());
        assertEquals("示例企业", result.getData().getList().get(0).getTargetName());
        verify(memberUserMapper, never()).selectBatchIds(any());
    }

}
