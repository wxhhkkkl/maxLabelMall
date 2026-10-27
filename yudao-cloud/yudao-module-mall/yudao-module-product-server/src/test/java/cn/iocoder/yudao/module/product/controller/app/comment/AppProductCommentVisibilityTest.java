package cn.iocoder.yudao.module.product.controller.app.comment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.app.comment.vo.AppCommentPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.comment.ProductCommentService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import cn.iocoder.yudao.module.product.service.subscriptionplan.MaxLabelSubscriptionPlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评价分页不得成为「不在公开商城出售的商品」的名称泄露通道（FR-294 / T111）。
 */
@ExtendWith(MockitoExtension.class)
public class AppProductCommentVisibilityTest {

    @Mock
    private ProductCommentService productCommentService;
    @Mock
    private ProductSpuService productSpuService;
    @Mock
    private MaxLabelSubscriptionPlanService subscriptionPlanService;
    @InjectMocks
    private AppProductCommentController controller;

    private AppCommentPageReqVO req(Long spuId) {
        AppCommentPageReqVO reqVO = new AppCommentPageReqVO();
        reqVO.setSpuId(spuId);
        return reqVO;
    }

    private ProductSpuDO spu(long id, boolean enterpriseOnly) {
        return ProductSpuDO.builder().id(id).name("商品").enterpriseOnly(enterpriseOnly).build();
    }

    @Test
    void enterpriseSpuReturnsEmptyPage() {
        when(productSpuService.getSpu(300L)).thenReturn(spu(300L, true));

        PageResult<?> page = controller.getCommentPage(req(300L)).getData();

        assertEquals(0, page.getList().size());
        verify(productCommentService, never()).getCommentPage(any(), anyBoolean());
    }

    @Test
    void planReferencedSpuReturnsEmptyPage() {
        when(productSpuService.getSpu(400L)).thenReturn(spu(400L, false));
        when(subscriptionPlanService.getPlanSpuIds(List.of(400L))).thenReturn(List.of(400L));

        PageResult<?> page = controller.getCommentPage(req(400L)).getData();

        assertEquals(0, page.getList().size());
        verify(productCommentService, never()).getCommentPage(any(), anyBoolean());
    }

    @Test
    void publicSpuQueriesCommentsAsBefore() {
        when(productSpuService.getSpu(200L)).thenReturn(spu(200L, false));
        when(subscriptionPlanService.getPlanSpuIds(List.of(200L))).thenReturn(List.of());
        when(productCommentService.getCommentPage(any(), anyBoolean())).thenReturn(PageResult.empty());

        controller.getCommentPage(req(200L));

        verify(productCommentService).getCommentPage(any(), anyBoolean());
    }
}
