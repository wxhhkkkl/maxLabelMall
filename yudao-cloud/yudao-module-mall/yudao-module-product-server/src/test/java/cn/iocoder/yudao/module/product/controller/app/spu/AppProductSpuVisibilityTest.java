package cn.iocoder.yudao.module.product.controller.app.spu;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.api.org.MemberOrgApi;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.history.ProductBrowseHistoryService;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import cn.iocoder.yudao.module.product.service.subscriptionplan.MaxLabelSubscriptionPlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_ENTERPRISE_ONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 公开 `list-by-ids` / `get-detail` 不得泄露企业专享商品（012 / R75）。
 */
@ExtendWith(MockitoExtension.class)
public class AppProductSpuVisibilityTest {

    @Mock
    private ProductSpuService productSpuService;
    @Mock
    private ProductSkuService productSkuService;
    @Mock
    private ProductBrowseHistoryService productBrowseHistoryService;
    @Mock
    private MemberOrgApi memberOrgApi;
    @Mock
    private MaxLabelSubscriptionPlanService subscriptionPlanService;
    @InjectMocks
    private AppProductSpuController controller;

    private ProductSpuDO spu(long id, String name, boolean enterpriseOnly) {
        return ProductSpuDO.builder()
                .id(id).name(name).enterpriseOnly(enterpriseOnly)
                .salesCount(0).virtualSalesCount(0)
                .build();
    }

    private MemberOrgRespDTO adminOrg() {
        MemberOrgRespDTO org = new MemberOrgRespDTO();
        org.setOrgId(88L);
        org.setRole(1);
        org.setStatus(0);
        return org;
    }

    @Test
    void anonymousListByIdsExcludesEnterpriseSpu() {
        when(productSpuService.getSpuList(any()))
                .thenReturn(List.of(spu(1L, "公开商品", false), spu(2L, "企业商品", true)));
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(null);
            var result = controller.getSpuList(Set.of(1L, 2L)).getData();
            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getId());
        }
    }

    @Test
    void anonymousListByIdsExcludesPlanReferencedSpu() {
        // 会员授权商品（被 maxlabel_subscription_plan 引用）不在公开商城出现（FR-328 / Q81）
        when(productSpuService.getSpuList(any()))
                .thenReturn(List.of(spu(1L, "公开商品", false), spu(3L, "会员授权商品", false)));
        when(subscriptionPlanService.getPlanSpuIds(any())).thenReturn(List.of(3L));
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(null);
            var result = controller.getSpuList(Set.of(1L, 3L)).getData();
            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getId());
        }
    }

    @Test
    void enterpriseAdminListByIdsIncludesEnterpriseSpu() {
        when(productSpuService.getSpuList(any()))
                .thenReturn(List.of(spu(1L, "公开商品", false), spu(2L, "企业商品", true)));
        when(memberOrgApi.getByMemberId(1001L)).thenReturn(success(adminOrg()));
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            var result = controller.getSpuList(Set.of(1L, 2L)).getData();
            assertEquals(2, result.size());
        }
    }

    @Test
    void detailRejectsEnterpriseSpu() {
        when(productSpuService.getSpu(2L)).thenReturn(spu(2L, "企业商品", true));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> controller.getSpuDetail(2L));
        assertEquals(SPU_ENTERPRISE_ONLY.getCode(), exception.getCode());
    }

}
