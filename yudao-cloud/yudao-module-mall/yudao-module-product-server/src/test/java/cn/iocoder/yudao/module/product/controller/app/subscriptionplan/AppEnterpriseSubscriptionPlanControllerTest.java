package cn.iocoder.yudao.module.product.controller.app.subscriptionplan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.api.org.MemberOrgApi;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.product.controller.app.subscriptionplan.vo.AppMaxLabelSubscriptionPlanRespVO;
import cn.iocoder.yudao.module.product.service.subscriptionplan.MaxLabelSubscriptionPlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.ENTERPRISE_PLAN_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AppEnterpriseSubscriptionPlanControllerTest {

    @Mock
    private MaxLabelSubscriptionPlanService planService;
    @Mock
    private MemberOrgApi memberOrgApi;
    @InjectMocks
    private AppEnterpriseSubscriptionPlanController controller;

    private MemberOrgRespDTO org(int role, int status) {
        MemberOrgRespDTO org = new MemberOrgRespDTO();
        org.setOrgId(88L);
        org.setRole(role);
        org.setStatus(status);
        return org;
    }

    private AppMaxLabelSubscriptionPlanRespVO plan() {
        AppMaxLabelSubscriptionPlanRespVO plan = new AppMaxLabelSubscriptionPlanRespVO();
        plan.setSpuId(500L);
        plan.setSkuId(601L);
        plan.setName("MaxLabel 企业授权");
        plan.setPeriodMonths(12);
        plan.setPrice(99000);
        return plan;
    }

    @Test
    void list_onlyForEnterpriseAdmin() {
        when(memberOrgApi.getByMemberId(1001L)).thenReturn(success(org(1, 0)));
        when(planService.getEnabledEnterprisePlanListWithProduct()).thenReturn(List.of(plan()));
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            var result = controller.getEnterprisePlanList().getData();
            assertEquals(1, result.size());
            assertEquals(601L, result.get(0).getSkuId());
        }
    }

    @Test
    void list_rejectsNonEnterpriseMember() {
        when(memberOrgApi.getByMemberId(1001L)).thenReturn(success(null));
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> controller.getEnterprisePlanList());
            assertEquals(ENTERPRISE_PLAN_FORBIDDEN.getCode(), exception.getCode());
        }
    }

    @Test
    void list_rejectsOrdinaryMember() {
        when(memberOrgApi.getByMemberId(1001L)).thenReturn(success(org(3, 0)));
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> controller.getEnterprisePlanList());
            assertEquals(ENTERPRISE_PLAN_FORBIDDEN.getCode(), exception.getCode());
        }
    }

    @Test
    void detail_returnsPlan() {
        when(memberOrgApi.getByMemberId(1001L)).thenReturn(success(org(2, 0)));
        when(planService.getEnterprisePlanDetail(601L)).thenReturn(plan());
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            var result = controller.getEnterprisePlanDetail(601L).getData();
            assertEquals("MaxLabel 企业授权", result.getName());
        }
    }

}
