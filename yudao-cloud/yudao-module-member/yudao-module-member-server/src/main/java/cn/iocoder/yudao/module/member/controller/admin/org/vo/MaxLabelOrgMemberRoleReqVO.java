package cn.iocoder.yudao.module.member.controller.admin.org.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MaxLabelOrgMemberRoleReqVO {

    @NotNull(message = "企业编号不能为空")
    private Long orgId;

    @NotNull(message = "会员编号不能为空")
    private Long memberId;

    @NotNull(message = "角色不能为空")
    private Integer role;

}
