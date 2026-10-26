package cn.iocoder.yudao.module.member.controller.admin.org.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MaxLabelOrgMemberPageReqVO extends PageParam {

    @NotNull(message = "企业编号不能为空")
    private Long orgId;

    private String keyword;

}
