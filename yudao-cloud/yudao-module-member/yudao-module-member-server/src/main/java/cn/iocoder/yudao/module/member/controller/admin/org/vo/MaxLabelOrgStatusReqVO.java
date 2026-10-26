package cn.iocoder.yudao.module.member.controller.admin.org.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MaxLabelOrgStatusReqVO {

    @NotNull(message = "企业编号不能为空")
    private Long id;

    @NotNull(message = "状态不能为空")
    private Integer status;

}
