package cn.iocoder.yudao.module.member.service.labelshop;

/**
 * LabelShop 登录失败（账号 / 密码 / 锁定 / 响应异常）。
 */
public class LabelshopLoginException extends RuntimeException {

    public LabelshopLoginException(String message) {
        super(message);
    }

}
