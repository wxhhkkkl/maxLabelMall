package cn.iocoder.yudao.module.member.controller.admin.license.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 授权客户明细分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class LicenseCustomerPageReqVO extends PageParam {

    @Schema(description = "客户类型：1 个人 2 企业；不传 = 全部", example = "1")
    private Integer targetType;

    @Schema(description = "授权状态：0 有效 1 已过期；不传 = 全部", example = "0")
    private Integer status;

    @Schema(description = "权益类型（T092）：1 订阅会员 2 席位会员；不传 = 全部", example = "2")
    private Integer rightsType;

    @Schema(description = "关键字：会员昵称 / 手机号 / 企业名", example = "小王")
    private String keyword;

}
