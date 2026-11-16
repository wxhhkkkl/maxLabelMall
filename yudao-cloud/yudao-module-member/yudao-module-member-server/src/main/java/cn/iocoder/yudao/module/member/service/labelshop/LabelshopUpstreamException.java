package cn.iocoder.yudao.module.member.service.labelshop;

/**
 * LabelShop 上游接口异常（非会话失效）。
 */
public class LabelshopUpstreamException extends RuntimeException {

    public LabelshopUpstreamException(String message) {
        super(message);
    }

}
