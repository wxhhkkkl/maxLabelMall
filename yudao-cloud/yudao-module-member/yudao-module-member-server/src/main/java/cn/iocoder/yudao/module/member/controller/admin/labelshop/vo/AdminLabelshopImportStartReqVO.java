package cn.iocoder.yudao.module.member.controller.admin.labelshop.vo;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发起历史模板导入。
 */
@Data
public class AdminLabelshopImportStartReqVO {

    /** 企业导入时必填：orgId 与 memberId 二选一 */
    private Long orgId;

    /** 个人会员代导时必填：orgId 与 memberId 二选一 */
    private Long memberId;

    @NotBlank(message = "历史系统账号不能为空")
    private String account;

    @NotBlank(message = "历史系统密码不能为空")
    private String password;

    @NotNull(message = "请确认已获得账号所有人授权")
    private Boolean authorized;

    @AssertTrue(message = "请指定企业或会员（且只能指定一个）")
    public boolean isSubjectValid() {
        return (orgId == null) != (memberId == null);
    }

}
