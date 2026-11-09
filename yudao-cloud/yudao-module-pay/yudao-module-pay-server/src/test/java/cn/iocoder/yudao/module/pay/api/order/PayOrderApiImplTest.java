package cn.iocoder.yudao.module.pay.api.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderSubmitReqDTO;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderSubmitRespDTO;
import cn.iocoder.yudao.module.pay.controller.admin.order.vo.PayOrderSubmitReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.order.vo.PayOrderSubmitRespVO;
import cn.iocoder.yudao.module.pay.service.order.PayOrderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PayOrderApiImpl#submitOrder} 的适配器测试（017）
 */
public class PayOrderApiImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private PayOrderApiImpl payOrderApi;

    @Mock
    private PayOrderService payOrderService;

    @Test
    void submitOrder_convertsRequestAndResponse() {
        PayOrderSubmitRespVO respVO = new PayOrderSubmitRespVO();
        respVO.setStatus(0);
        respVO.setDisplayMode("qr_code");
        respVO.setDisplayContent("weixin://wxpay/bizpayurl?pr=test");
        when(payOrderService.submitOrder(any(), eq("127.0.0.1"))).thenReturn(respVO);

        PayOrderSubmitReqDTO reqDTO = new PayOrderSubmitReqDTO();
        reqDTO.setId(987L);
        reqDTO.setChannelCode("wx_native");
        CommonResult<PayOrderSubmitRespDTO> result = payOrderApi.submitOrder(reqDTO, "127.0.0.1");

        ArgumentCaptor<PayOrderSubmitReqVO> captor = ArgumentCaptor.forClass(PayOrderSubmitReqVO.class);
        verify(payOrderService).submitOrder(captor.capture(), eq("127.0.0.1"));
        assertEquals(987L, captor.getValue().getId());
        assertEquals("wx_native", captor.getValue().getChannelCode());

        assertEquals(0, result.getData().getStatus());
        assertEquals("qr_code", result.getData().getDisplayMode());
        assertEquals("weixin://wxpay/bizpayurl?pr=test", result.getData().getDisplayContent());
    }

}
