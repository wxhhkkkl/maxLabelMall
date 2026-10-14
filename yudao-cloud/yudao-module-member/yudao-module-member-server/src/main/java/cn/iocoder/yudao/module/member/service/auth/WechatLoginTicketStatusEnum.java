package cn.iocoder.yudao.module.member.service.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 微信扫码登录的票据状态
 *
 * 对应 `specs/005-wechat-scan-login/data-model.md` §4 的 **5 个状态**：
 * `待扫 / 待补全 / 已完成 / 已过期 / 已作废`。
 *
 * ⚠️ **只有 `待扫`、`待补全`、`已完成`、`已作废` 会被真正写进 Redis**；
 * `已过期` **不是存储态** —— 它表示"票据在 Redis 里已经不存在了"
 * （TTL 自然到期，或已被取走删除）。这样设计是因为 Redis 自己就会过期，
 * 再往库里存一个"已过期"标记反而要额外清理，且永远清不干净。
 *
 * ⚠️ **没有「已扫待确认」**：公众号网页授权**不回调"用户已扫"事件** ——
 * 从客户扫码到服务端拿到 code，中间是微信自己的页面。服务端能观测到的第一个变化就是「待补全」。
 */
@Getter
@AllArgsConstructor
public enum WechatLoginTicketStatusEnum {

    WAITING_SCAN(1, "待扫"),
    WAITING_PROFILE(2, "待补全"),
    COMPLETED(3, "已完成"),
    /**
     * 仅用于对外表达，**不会是存储态** —— 见类注释
     */
    EXPIRED(4, "已过期"),
    ABANDONED(5, "已作废"),
    ;

    private final Integer status;
    private final String description;

    public static WechatLoginTicketStatusEnum of(Integer status) {
        for (WechatLoginTicketStatusEnum value : values()) {
            if (value.getStatus().equals(status)) {
                return value;
            }
        }
        return null;
    }

}
