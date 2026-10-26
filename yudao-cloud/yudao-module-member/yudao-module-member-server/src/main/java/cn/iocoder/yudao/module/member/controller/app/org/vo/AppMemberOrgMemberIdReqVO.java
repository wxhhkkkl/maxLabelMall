package cn.iocoder.yudao.module.member.controller.app.org.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AppMemberOrgMemberIdReqVO {

    @NotNull(message = "会员编号不能为空")
    private Long memberId;

}
