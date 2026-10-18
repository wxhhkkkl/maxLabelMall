package cn.iocoder.yudao.module.system.api.social.dto;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * RPC 服务 - 绑定社交用户（**按 openid**）Request DTO
 *
 * 与 {@link SocialUserBindReqDTO} 的区别：那个要 **code + state**（用授权码现换 openid），
 * 这个直接给 **openid**。
 *
 * 为什么需要它（`specs/005-wechat-scan-login/research.md` R16）：
 * 微信扫码登录把"建号 + 绑定"推迟到了客户**提交补全页**那一刻，而那时离扫码已过去一段时间 ——
 * 微信的授权码是**一次性且短命**的，多半已经失效。所以扫码那一刻就把 code 换成 openid 存进票据，
 * 提交时按 **openid** 绑。
 */
@Schema(description = "RPC 服务 - 绑定社交用户（按 openid）Request DTO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SocialUserBindByOpenidReqDTO {

    @Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "用户编号不能为空")
    private Long userId;
    @Schema(description = "用户类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @InEnum(UserTypeEnum.class)
    @NotNull(message = "用户类型不能为空")
    private Integer userType;

    @Schema(description = "社交平台的类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "31")
    @InEnum(SocialTypeEnum.class)
    @NotNull(message = "社交平台的类型不能为空")
    private Integer socialType;
    @Schema(description = "社交平台的 openid", requiredMode = Schema.RequiredMode.REQUIRED, example = "o1w6w5...")
    @NotEmpty(message = "openid 不能为空")
    private String openid;

}
