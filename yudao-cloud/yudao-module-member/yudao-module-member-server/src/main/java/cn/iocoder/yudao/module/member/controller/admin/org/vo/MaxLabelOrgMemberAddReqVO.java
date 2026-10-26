package cn.iocoder.yudao.module.member.controller.admin.org.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MaxLabelOrgMemberAddReqVO {

    @NotNull(message = "企业编号不能为空")
    private Long orgId;

    @NotBlank(message = "手机号不能为空")
    private String mobile;

}
