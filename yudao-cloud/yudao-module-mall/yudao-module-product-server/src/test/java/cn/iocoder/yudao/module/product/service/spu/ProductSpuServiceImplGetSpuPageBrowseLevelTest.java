package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.service.filter.ProductSpuFilterTagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品分页的身份解析（019 / FR-525）：访客、登录无等级、登录有等级三种传参。
 */
@ExtendWith(MockitoExtension.class)
public class ProductSpuServiceImplGetSpuPageBrowseLevelTest {

    @Mock
    private ProductSpuMapper productSpuMapper;
    @Mock
    private ProductSpuBrowseLevelService browseLevelService;
    @Mock
    private ProductSpuFilterTagService spuFilterTagService;

    @InjectMocks
    private ProductSpuServiceImpl productSpuService;

    private AppProductSpuPageReqVO req() {
        AppProductSpuPageReqVO reqVO = new AppProductSpuPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        return reqVO;
    }

    @Test
    void guestPassesGuestContext() {
        when(productSpuMapper.selectPage(any(), any(), anyBoolean(), any()))
                .thenReturn(new PageResult<>(Collections.<ProductSpuDO>emptyList(), 0L));
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(null);
            productSpuService.getSpuPage(req());
        }
        verify(productSpuMapper).selectPage(any(), eq(Set.of()), eq(false), isNull());
    }

    @Test
    void loggedInWithoutLevelPassesNullLevel() {
        when(productSpuMapper.selectPage(any(), any(), anyBoolean(), any()))
                .thenReturn(new PageResult<>(Collections.<ProductSpuDO>emptyList(), 0L));
        when(browseLevelService.getViewerLevelId(1001L)).thenReturn(null);
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            productSpuService.getSpuPage(req());
        }
        verify(productSpuMapper).selectPage(any(), eq(Set.of()), eq(true), isNull());
    }

    @Test
    void loggedInWithLevelPassesLevelId() {
        when(productSpuMapper.selectPage(any(), any(), anyBoolean(), any()))
                .thenReturn(new PageResult<>(Collections.<ProductSpuDO>emptyList(), 0L));
        when(browseLevelService.getViewerLevelId(1001L)).thenReturn(5L);
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            productSpuService.getSpuPage(req());
        }
        verify(productSpuMapper).selectPage(any(), eq(Set.of()), eq(true), eq(5L));
    }

}
