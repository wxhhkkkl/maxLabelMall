package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.offline;

import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundUnifiedReqDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.transfer.PayTransferRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.transfer.PayTransferUnifiedReqDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.AbstractPayClient;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.NonePayClientConfig;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 对公转账（线下打款）的 PayClient 实现类（008）
 *
 * **钱不经过任何支付平台** —— 客户把钱打进公司银行账户、财务在后台确认收款；
 * 系统只负责**记账**。所以本实现**不调用任何三方接口**。
 *
 * ⚠️ **两个方法的行为是刻意相反的，别"改成一致"**：
 *
 * · {@link #doUnifiedOrder} —— **抛异常**。对公单的支付单是"确认收款"时**直接造成已支付**的
 *   （见 `PayOrderApi` 的那个能力），**永远不会**走"发起支付"这条路。
 *   万一哪天界面上多出一个"去支付"，这里抛错会让它**当场失败** ——
 *   而如果学 `MockPayClient` 返回成功，就会把**钱还没到账的订单标成已支付**，那是最坏的后果。
 *
 * · {@link #doUnifiedRefund} —— **返回成功**。退款**必须**能走通（SC-073）：
 *   `PayRefundServiceImpl` 会 `getPayClient(channelId)` 拿本类去发起退款。
 *   ⚠️ 但这里的"成功"**只是记账** —— 对公收款打进的是银行账户，钱退不回去，
 *   实际由**财务线下银行转账**完成（spec 的 Q47，所有者已知情并接受：
 *   **客户会先看到「已退款」，而钱还没到账**）。
 *
 * @author 芋道源码
 */
public class OfflinePayClient extends AbstractPayClient<NonePayClientConfig> {

    private static final String OFFLINE_RESP_SUCCESS_DATA = "OFFLINE_SUCCESS";

    public OfflinePayClient(Long channelId, NonePayClientConfig config) {
        super(channelId, PayChannelEnum.OFFLINE.getCode(), config);
    }

    @Override
    protected void doInit() {
    }

    /**
     * ⚠️ **必须抛异常**（见类注释）—— 对公单不存在"在线发起支付"这个动作
     */
    @Override
    protected PayOrderRespDTO doUnifiedOrder(PayOrderUnifiedReqDTO reqDTO) {
        throw new UnsupportedOperationException(
                "对公转账不支持在线发起支付：客户线下打款后，由财务在后台「确认收款」");
    }

    @Override
    protected PayOrderRespDTO doGetOrder(String outTradeNo) {
        throw new UnsupportedOperationException("对公转账不支持在线查询支付状态");
    }

    /**
     * 退款：本地直接返回成功（**只是记账**，见类注释）
     */
    @Override
    protected PayRefundRespDTO doUnifiedRefund(PayRefundUnifiedReqDTO reqDTO) {
        return PayRefundRespDTO.successOf("OFFLINE-R-" + reqDTO.getOutRefundNo(), LocalDateTime.now(),
                reqDTO.getOutRefundNo(), OFFLINE_RESP_SUCCESS_DATA);
    }

    @Override
    protected PayRefundRespDTO doGetRefund(String outTradeNo, String outRefundNo) {
        return PayRefundRespDTO.successOf("OFFLINE-R-" + outRefundNo, LocalDateTime.now(),
                outRefundNo, OFFLINE_RESP_SUCCESS_DATA);
    }

    @Override
    protected PayOrderRespDTO doParseOrderNotify(Map<String, String> params, String body, Map<String, String> headers) {
        throw new UnsupportedOperationException("对公转账没有支付回调");
    }

    @Override
    protected PayRefundRespDTO doParseRefundNotify(Map<String, String> params, String body, Map<String, String> headers) {
        throw new UnsupportedOperationException("对公转账没有退款回调");
    }

    @Override
    protected PayTransferRespDTO doUnifiedTransfer(PayTransferUnifiedReqDTO reqDTO) {
        throw new UnsupportedOperationException("对公转账不支持转账");
    }

    @Override
    protected PayTransferRespDTO doParseTransferNotify(Map<String, String> params, String body, Map<String, String> headers) {
        throw new UnsupportedOperationException("对公转账不支持转账");
    }

    @Override
    protected PayTransferRespDTO doGetTransfer(String outTradeNo) {
        throw new UnsupportedOperationException("对公转账不支持转账");
    }

}
