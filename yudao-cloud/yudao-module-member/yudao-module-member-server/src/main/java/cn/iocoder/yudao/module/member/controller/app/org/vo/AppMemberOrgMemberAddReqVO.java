package cn.iocoder.yudao.module.member.controller.app.org.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AppMemberOrgMemberAddReqVO {

    @NotBlank(message = "手机号不能为空")
    private String mobile;

}
