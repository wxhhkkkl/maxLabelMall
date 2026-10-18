package cn.iocoder.yudao.module.member.service.auth;

import cn.iocoder.yudao.module.member.service.auth.bo.WechatLoginTicketBO;

/**
 * 微信扫码登录的「登录票据」Service
 *
 * 一次扫码登录的完整生命周期都由它管：
 * ```
 * 待扫 ──手机侧授权完成（code 到达）──> 待补全 ──提交成功──> 已完成 ──(轮询取走 token)──> 删除
 *   │                                     │
 *   └──────────── 超时 ───────────────────┴──> 已过期
 *                                        └── 客户关闭补全页 ──> 已作废
 * ```
 *
 * 契约见 `specs/005-wechat-scan-login/contracts/app-api.md`。
 */
public interface WechatLoginTicketService {

    /**
     * 签发一张新票据（PC 点「微信登录」时调）
     *
     * ⚠️ **`state` 必须由调用方传进来，而且必须是「微信授权地址里那个」**：
     * justauth 在生成授权地址时会**自己生成一个 state 并缓存**，回调时按它比对；
     * 若我们自己另生成一个、把地址里的换掉，justauth 就会抛 `Illegal state [WECHAT_MP]`
     * （2026-10-14 线上实测踩过）。
     *
     * @param state 微信授权地址里带的 state（由 justauth 生成、它自己缓存着）
     * @return 票据，状态为「待扫」
     */
    WechatLoginTicketBO create(String state);

    /**
     * 票据的存活秒数 —— 对外暴露，好让签发接口如实告诉前端"这个二维码多久会过期"（FR-152 / FR-140）
     */
    long getTtlSeconds();

    /**
     * 按票据号查。**不存在（含已过期、已被取走）一律返回 null**
     */
    WechatLoginTicketBO get(String ticket);

    /**
     * 按微信授权的 `state` 查 —— 微信回调只带 state，不带 ticket
     */
    WechatLoginTicketBO getByState(String state);

    /**
     * 手机侧授权完成：把微信身份写进票据，状态 → 「待补全」。
     *
     * ⚠️ **这一步只识别身份，MUST NOT 建会员、MUST NOT 建绑定、MUST NOT 签发令牌**（FR-132 / FR-150）
     */
    void markWaitingProfile(String state, String openid, String wxNickname, String wxAvatar);

    /**
     * 提交补全成功：写入会员与令牌，状态 → 「已完成」。
     *
     * 这是**唯一**能让票据带上 `memberId` / `accessToken` 的入口（FR-150）
     */
    void markCompleted(String ticket, Long memberId, String accessToken, String refreshToken);

    /**
     * 客户关掉了补全页：这次扫码彻底作废（FR-148）。
     *
     * 幂等；对已不存在的票据调用不报错
     */
    void abandon(String ticket);

    /**
     * 取走令牌后删除票据 —— 一次性（FR-149）
     */
    void remove(String ticket);

}
