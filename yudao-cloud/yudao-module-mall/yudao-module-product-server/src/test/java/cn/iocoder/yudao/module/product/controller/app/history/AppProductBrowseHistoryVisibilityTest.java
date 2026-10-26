package cn.iocoder.yudao.module.product.controller.app.history;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.api.org.MemberOrgApi;
import cn.iocoder.yudao.module.product.controller.app.history.vo.AppProductBrowseHistoryPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.history.ProductBrowseHistoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.history.ProductBrowseHistoryService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 非企业管理员的浏览记录不展示企业专享商品（012 / R75 / T090）。
 */
@ExtendWith(MockitoExtension.class)
public class AppProductBrowseHistoryVisibilityTest {

    @Mock
    private ProductBrowseHistoryService productBrowseHistoryService;
    @Mock
    private ProductSpuService productSpuService;
    @Mock
    private MemberOrgApi memberOrgApi;
    @InjectMocks
    private AppProductBrowseHistoryController controller;

    private ProductSpuDO spu(long id, String name, boolean enterpriseOnly) {
        return ProductSpuDO.builder()
                .id(id).name(name).picUrl("").price(100).stock(10)
                .salesCount(0).virtualSalesCount(0)
                .enterpriseOnly(enterpriseOnly)
                .build();
    }

    @Test
    void nonAdminHistoryExcludesEnterpriseSpu() {
        ProductBrowseHistoryDO publicHistory = ProductBrowseHistoryDO.builder()
                .id(1L).userId(1001L).spuId(1L).build();
        ProductBrowseHistoryDO enterpriseHistory = ProductBrowseHistoryDO.builder()
                .id(2L).userId(1001L).spuId(2L).build();
        when(productBrowseHistoryService.getBrowseHistoryPage(any()))
                .thenReturn(new PageResult<>(List.of(publicHistory, enterpriseHistory), 2L));
        when(productSpuService.getSpuList(any()))
                .thenReturn(List.of(spu(1L, "公开商品", false), spu(2L, "企业商品", true)));
        when(memberOrgApi.getByMemberId(1001L)).thenReturn(success(null));

        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            var page = controller.getBrowseHistoryPage(new AppProductBrowseHistoryPageReqVO()).getData();
            assertEquals(1, page.getList().size());
            assertEquals(1L, page.getList().get(0).getSpuId());
        }
    }

}
