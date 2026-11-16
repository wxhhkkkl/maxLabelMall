package cn.iocoder.yudao.module.member.service.labelshop;

/**
 * LabelShop 会话失效，需要重登一次。
 */
public class LabelshopSessionExpiredException extends RuntimeException {

    public LabelshopSessionExpiredException(String message) {
        super(message);
    }

}
