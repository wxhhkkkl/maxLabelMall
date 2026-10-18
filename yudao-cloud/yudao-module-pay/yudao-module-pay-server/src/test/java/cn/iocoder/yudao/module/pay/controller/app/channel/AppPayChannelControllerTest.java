package cn.iocoder.yudao.module.pay.controller.app.channel;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.service.channel.PayChannelService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link AppPayChannelController} 的单元测试（008）
 *
 * ⚠️ 只钉一件事：**「对公转账」（`offline`）不能出现在"支付页能选的渠道"里**。
 *
 * 它在 `pay_channel` 里是**启用**的（退款要按渠道取 `PayClient`），于是会跟着
 * `getEnableChannelList` 一起返回 —— 而 C 端对**没登记过的编码**是"回落用编码当名字"，
 * 界面上就会多出一个名叫「offline」的渠道；客户真去点它必然失败
 * （`OfflinePayClient.doUnifiedOrder` 直接抛异常）。
 *
 * 不滤掉的后果是**线上可见**的：普通待支付订单的支付页多一个点不动的「offline」（2026-10-16 用户看到）。
 */
public class AppPayChannelControllerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AppPayChannelController controller;

    @Mock
    private PayChannelService channelService;

    @Test
    public void testGetEnableChannelCodeList_excludeOffline() {
        PayChannelDO alipay = new PayChannelDO();
        alipay.setCode(PayChannelEnum.ALIPAY_PC.getCode());
        PayChannelDO offline = new PayChannelDO();
        offline.setCode(PayChannelEnum.OFFLINE.getCode());
        when(channelService.getEnableChannelList(eq(10L))).thenReturn(List.of(alipay, offline));

        Set<String> codes = controller.getEnableChannelCodeList(10L).getCheckedData();

        assertTrue(codes.contains(PayChannelEnum.ALIPAY_PC.getCode())); // 在线渠道照旧
        assertFalse(codes.contains(PayChannelEnum.OFFLINE.getCode()),
                "对公转账是**下单时**选的，不能作为支付页的可选渠道");
    }

    @Test
    public void testGetEnableChannelCodeList_otherChannelsUntouched() {
        PayChannelDO wx = new PayChannelDO();
        wx.setCode(PayChannelEnum.WX_NATIVE.getCode());
        when(channelService.getEnableChannelList(eq(10L))).thenReturn(List.of(wx));

        assertEquals(Set.of(PayChannelEnum.WX_NATIVE.getCode()),
                controller.getEnableChannelCodeList(10L).getCheckedData());
    }

}
