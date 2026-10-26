package cn.iocoder.yudao.module.member.controller.admin.org.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MaxLabelOrgSaveReqVO {

    private Long id;

    @NotBlank(message = "企业名称不能为空")
    private String name;

    @NotNull(message = "企业 OWNER 不能为空")
    private Long ownerMemberId;

    private String remark;

}
