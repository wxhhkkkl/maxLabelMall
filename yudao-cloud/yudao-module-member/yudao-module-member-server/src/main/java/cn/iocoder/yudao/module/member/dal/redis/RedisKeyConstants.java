package cn.iocoder.yudao.module.member.dal.redis;

/**
 * Member Redis Key 枚举类
 *
 * 照 system 模块的同名文件（`cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants`）的写法。
 */
public interface RedisKeyConstants {

    /**
     * 微信扫码登录的票据
     * <p>
     * KEY 格式：wechat_login_ticket:{ticket}
     * VALUE 数据类型：String 票据的 JSON
     */
    String WECHAT_LOGIN_TICKET = "wechat_login_ticket:%s";

    /**
     * 微信授权的 state → 票据 的映射
     * <p>
     * KEY 格式：wechat_login_state:{state}
     * VALUE 数据类型：String 票据号
     * <p>
     * 为什么要这个映射：微信回调只带 `state`，而票据是按 `ticket` 存的；
     * 且**两者刻意不是同一个值**（state 会出现在二维码与回跳 URL 里，见 research.md R19）。
     */
    String WECHAT_LOGIN_STATE = "wechat_login_state:%s";

}
