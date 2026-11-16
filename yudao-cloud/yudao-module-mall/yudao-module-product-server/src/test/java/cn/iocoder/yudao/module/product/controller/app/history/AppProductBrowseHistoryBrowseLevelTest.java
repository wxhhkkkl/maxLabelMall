package cn.iocoder.yudao.module.product.controller.app.history;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.api.org.MemberOrgApi;
import cn.iocoder.yudao.module.product.controller.app.history.vo.AppProductBrowseHistoryPageReqVO;
import cn.iocoder.yudao.module.product.service.history.ProductBrowseHistoryService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuBrowseLevelService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 浏览历史分页必须把当前等级传给 Service（019 / FR-537）
 */
@ExtendWith(MockitoExtension.class)
public class AppProductBrowseHistoryBrowseLevelTest {

    @Mock
    private ProductBrowseHistoryService productBrowseHistoryService;
    @Mock
    private ProductSpuService productSpuService;
    @Mock
    private MemberOrgApi memberOrgApi;
    @Mock
    private ProductSpuBrowseLevelService browseLevelService;
    @InjectMocks
    private AppProductBrowseHistoryController controller;

    @Test
    void passesViewerLevelToService() {
        when(browseLevelService.getViewerLevelId(1001L)).thenReturn(5L);
        when(memberOrgApi.getByMemberId(1001L)).thenReturn(success(null));
        when(productBrowseHistoryService.getBrowseHistoryPageExcludingEnterprise(any(), eq(5L)))
                .thenReturn(new PageResult<>(Collections.emptyList(), 0L));

        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            controller.getBrowseHistoryPage(new AppProductBrowseHistoryPageReqVO());
        }
        verify(productBrowseHistoryService).getBrowseHistoryPageExcludingEnterprise(any(), eq(5L));
    }

}
