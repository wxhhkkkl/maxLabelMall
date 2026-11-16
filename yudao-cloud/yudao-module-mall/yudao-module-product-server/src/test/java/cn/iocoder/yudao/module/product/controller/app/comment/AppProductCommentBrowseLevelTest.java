package cn.iocoder.yudao.module.product.controller.app.comment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.app.comment.vo.AppCommentPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.comment.ProductCommentService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuBrowseLevelService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import cn.iocoder.yudao.module.product.service.subscriptionplan.MaxLabelSubscriptionPlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评价分页的浏览等级过滤（019 / FR-526）
 */
@ExtendWith(MockitoExtension.class)
public class AppProductCommentBrowseLevelTest {

    @Mock
    private ProductCommentService productCommentService;
    @Mock
    private ProductSpuService productSpuService;
    @Mock
    private MaxLabelSubscriptionPlanService subscriptionPlanService;
    @Mock
    private ProductSpuBrowseLevelService browseLevelService;
    @InjectMocks
    private AppProductCommentController controller;

    @Test
    void hiddenLevelSpuReturnsEmptyPage() {
        ProductSpuDO spu = ProductSpuDO.builder()
                .id(3L).name("等级商品").enterpriseOnly(false).browseLevelType(2).build();
        when(productSpuService.getSpu(3L)).thenReturn(spu);
        when(browseLevelService.isVisible(spu, null)).thenReturn(false);

        AppCommentPageReqVO reqVO = new AppCommentPageReqVO();
        reqVO.setSpuId(3L);
        PageResult<?> page = controller.getCommentPage(reqVO).getData();

        assertEquals(0, page.getList().size());
        verify(productCommentService, never()).getCommentPage(any(), anyBoolean());
    }

}
