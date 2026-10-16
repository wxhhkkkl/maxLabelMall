package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.offline;

import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.enums.refund.PayRefundStatusEnum;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundUnifiedReqDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.exception.PayClientException;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.NonePayClientConfig;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.PayClientFactoryImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OfflinePayClient} 的单元测试（008 / R46 / SC-073）
 *
 * 本类**不依赖 Spring**（不碰 DB、不碰 Redis）—— 对公转账的客户端本来就"不调用任何三方"，
 * 所以纯对象就能测。与 `PayClientFactoryImplIntegrationTest` 的区别是：那个要真凭据（`@Disabled`），
 * 这里验的是**本地行为**。
 *
 * 要钉住的三条：
 * · **工厂里必须注册 `offline`** —— `PayClientFactoryImpl.createPayClient` 取不到类会直接
 *   `Assert.notNull` 抛错；R46 的初版就是因为漏了这一点而写错（"offline 不发起三方调用，
 *   所以不用写 client"），**漏看了退款**会走 `channelService.getPayClient(channelId)`
 * · **下单必须失败**（抛异常）：对公单的支付单是确认收款时直接造成已支付的，
 *   一旦这里"返回成功"，就会把钱还没到账的订单标成已支付
 * · **退款返回成功**：渠道虽不真实退钱，但链路必须通（钱由财务线下转回，Q47）
 */
public class OfflinePayClientTest {

    private static final Long CHANNEL_ID = 20L;

    private final PayClientFactoryImpl payClientFactory = new PayClientFactoryImpl();

    /** 走**工厂**取客户端（而不是 new）—— 正是为了验"渠道注册了没有" */
    private OfflinePayClient createClient() {
        payClientFactory.createOrUpdatePayClient(CHANNEL_ID, PayChannelEnum.OFFLINE.getCode(),
                new NonePayClientConfig());
        return (OfflinePayClient) payClientFactory.getPayClient(CHANNEL_ID);
    }

    @Test
    public void testFactory_offlineClientRegistered() {
        // 少一条注册，`PayOrderServiceImpl.validateChannelCanSubmit` 就会拿不到 client 而报
        // 「渠道不存在」；而**退款**（PayRefundServiceImpl 会 getPayClient）会直接崩
        assertNull(payClientFactory.getPayClient(CHANNEL_ID)); // 注册前拿不到

        OfflinePayClient client = createClient();

        assertNotNull(client);
        assertEquals(CHANNEL_ID, client.getId());
        assertNotNull(client.getConfig());
    }

    @Test
    public void testUnifiedOrder_alwaysThrow() {
        OfflinePayClient client = createClient();
        PayOrderUnifiedReqDTO reqDTO = new PayOrderUnifiedReqDTO();
        reqDTO.setOutTradeNo("OFFLINE-NO-1");
        reqDTO.setUserIp("127.0.0.1");
        reqDTO.setSubject("订单 900");
        reqDTO.setPrice(10800);
        reqDTO.setExpireTime(LocalDateTime.now().plusHours(2));
        reqDTO.setNotifyUrl("http://127.0.0.1/notify");

        // ⚠️ **必须抛**，不能返回"已支付"—— 钱还没到账
        // （`AbstractPayClient.unifiedOrder` 会把 doUnifiedOrder 抛出的 Throwable
        //   包成 PayClientException，所以这里断言的是后者）
        assertThrows(PayClientException.class, () -> client.unifiedOrder(reqDTO));
    }

    @Test
    public void testUnifiedRefund_success() {
        OfflinePayClient client = createClient();
        PayRefundUnifiedReqDTO reqDTO = new PayRefundUnifiedReqDTO();
        reqDTO.setOutTradeNo("OFFLINE-NO-1");
        reqDTO.setOutRefundNo("OFFLINE-R-1");
        reqDTO.setPayPrice(10800);
        reqDTO.setRefundPrice(10800);
        reqDTO.setReason("客户取消");
        reqDTO.setNotifyUrl("http://127.0.0.1/notify");

        PayRefundRespDTO resp = client.unifiedRefund(reqDTO);

        // 对公场景下这个"成功"**只是记账** —— 钱由财务线下转回（Q47，所有者已知情）
        assertEquals(PayRefundStatusEnum.SUCCESS.getStatus(), resp.getStatus());
        assertEquals("OFFLINE-R-1", resp.getOutRefundNo());
        assertNotNull(resp.getSuccessTime());
    }

    @Test
    public void testGetOrder_alwaysThrow() {
        // 没有线上支付状态可查 —— 问它等于用错了方法
        OfflinePayClient client = createClient();
        assertThrows(PayClientException.class, () -> client.getOrder("OFFLINE-NO-1"));
    }

}
