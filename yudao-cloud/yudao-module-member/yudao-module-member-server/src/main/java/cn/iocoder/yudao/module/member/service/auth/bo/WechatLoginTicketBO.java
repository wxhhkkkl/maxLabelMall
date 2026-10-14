package cn.iocoder.yudao.module.member.service.auth.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信扫码登录的一次「待扫」任务（登录票据）
 *
 * 对应 `specs/005-wechat-scan-login/data-model.md` §4。
 * **只活在 Redis 里**：不进数据库、不进备份、不进任何业务查询；用完即弃。
 *
 * ⚠️ 它是 Q26（延后建号）的载体：在"扫完了但还没填补全页"的那段时间里，
 * **微信身份只存在这里**（`openid` / `wxNickname` / `wxAvatar`），还没落成会员。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatLoginTicketBO {

    /**
     * 票据本身（同时是 Redis 的键名）。
     *
     * ⚠️ MUST 不可猜测（FR-149）—— 在"待补全"期间它**等价于登录凭据**。
     */
    private String ticket;

    /**
     * 微信授权的防伪串。
     *
     * 它会出现在二维码内容与微信回跳 URL 里（可能被截图、可能进日志），
     * 所以**与 ticket 刻意不是同一个值**（research.md R19）。
     */
    private String state;

    /**
     * 状态，见 {@link cn.iocoder.yudao.module.member.service.auth.WechatLoginTicketStatusEnum}
     */
    private Integer status;

    /**
     * 扫码者的微信身份。**这就是 Q26 里"先识别、不建号"的那个身份**
     */
    private String openid;

    /**
     * 微信侧带来的昵称 / 头像 —— 补全页的默认展示值（拿不到则为空）
     */
    private String wxNickname;
    private String wxAvatar;

    /**
     * 归属的会员。**`已完成` 之前必须为 null**（FR-150 的数据形态：提交成功前不建会员）
     */
    private Long memberId;

    /**
     * 登录令牌。**同样只在 `已完成` 之后才有**（FR-150）
     */
    private String accessToken;
    private String refreshToken;

}
