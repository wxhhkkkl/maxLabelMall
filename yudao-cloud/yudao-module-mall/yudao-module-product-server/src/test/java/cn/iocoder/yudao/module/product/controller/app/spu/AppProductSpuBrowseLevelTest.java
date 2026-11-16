package cn.iocoder.yudao.module.product.controller.app.spu;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.api.org.MemberOrgApi;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.history.ProductBrowseHistoryService;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuBrowseLevelService;
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

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * C 端商品列表与详情的浏览等级过滤（019 / FR-522 ~ FR-526、FR-532）
 */
@ExtendWith(MockitoExtension.class)
public class AppProductSpuBrowseLevelTest {

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
    @Mock
    private ProductSpuBrowseLevelService browseLevelService;
    @InjectMocks
    private AppProductSpuController controller;

    private ProductSpuDO spu(long id, String name, Integer type, Long... levelIds) {
        return ProductSpuDO.builder()
                .id(id).name(name)
                .browseLevelType(type)
                .browseLevelIds(levelIds.length == 0 ? null : List.of(levelIds))
                .enterpriseOnly(false)
                .salesCount(0).virtualSalesCount(0)
                .build();
    }

    @Test
    void guestListExcludesLoginAndLevelSpu() {
        ProductSpuDO publicSpu = spu(1L, "公开", 0);
        ProductSpuDO loginSpu = spu(2L, "登录", 1);
        ProductSpuDO levelSpu = spu(3L, "等级", 2, 5L);
        when(productSpuService.getSpuList(any())).thenReturn(List.of(publicSpu, loginSpu, levelSpu));
        when(browseLevelService.isVisible(publicSpu, null, null)).thenReturn(true);
        when(browseLevelService.isVisible(loginSpu, null, null)).thenReturn(false);
        when(browseLevelService.isVisible(levelSpu, null, null)).thenReturn(false);

        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(null);
            var result = controller.getSpuList(Set.of(1L, 2L, 3L)).getData();
            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getId());
        }
    }

    @Test
    void memberListKeepsLoginSpuButHidesUnmatchedLevelSpu() {
        ProductSpuDO publicSpu = spu(1L, "公开", 0);
        ProductSpuDO loginSpu = spu(2L, "登录", 1);
        ProductSpuDO levelSpu = spu(3L, "等级", 2, 6L);
        when(productSpuService.getSpuList(any())).thenReturn(List.of(publicSpu, loginSpu, levelSpu));
        when(memberOrgApi.getByMemberId(1001L)).thenReturn(success(null));
        when(browseLevelService.getViewerLevelId(1001L)).thenReturn(5L);
        when(browseLevelService.isVisible(publicSpu, 1001L, 5L)).thenReturn(true);
        when(browseLevelService.isVisible(loginSpu, 1001L, 5L)).thenReturn(true);
        when(browseLevelService.isVisible(levelSpu, 1001L, 5L)).thenReturn(false);

        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            var result = controller.getSpuList(Set.of(1L, 2L, 3L)).getData();
            assertEquals(2, result.size());
        }
    }

    @Test
    void detailHiddenSpuReportsNotExists() {
        ProductSpuDO hidden = spu(3L, "等级", 2, 6L);
        when(productSpuService.getSpu(3L)).thenReturn(hidden);
        doThrow(exception(SPU_NOT_EXISTS)).when(browseLevelService).validateVisible(hidden);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> controller.getSpuDetail(3L));
        assertEquals(SPU_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void enterpriseAdminIsNotExemptFromBrowseLevel() {
        // 企业专享 + 指定等级：管理员身份只解开企业专享，浏览等级仍按会员等级判定
        ProductSpuDO enterpriseLevelSpu = spu(4L, "企业等级", 2, 6L);
        enterpriseLevelSpu.setEnterpriseOnly(true);
        when(productSpuService.getSpuList(any())).thenReturn(List.of(enterpriseLevelSpu));
        when(memberOrgApi.getByMemberId(1001L)).thenReturn(success(adminOrg()));
        when(browseLevelService.getViewerLevelId(1001L)).thenReturn(5L);
        when(browseLevelService.isVisible(enterpriseLevelSpu, 1001L, 5L)).thenReturn(false);

        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            var result = controller.getSpuList(Set.of(4L)).getData();
            assertEquals(0, result.size());
        }
    }

    private MemberOrgRespDTO adminOrg() {
        MemberOrgRespDTO org = new MemberOrgRespDTO();
        org.setOrgId(88L);
        org.setRole(1);
        org.setStatus(0);
        return org;
    }

}
