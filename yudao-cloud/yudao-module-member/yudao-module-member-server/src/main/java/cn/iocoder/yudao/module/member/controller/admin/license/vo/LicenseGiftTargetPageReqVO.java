package cn.iocoder.yudao.module.member.controller.admin.license.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 赠送对象分页 Request VO（020 / Q162）
 *
 * 与「客户授权明细」不同：这里列出**全部**注册会员与全部企业，不限是否已有授权。
 */
@Schema(description = "管理后台 - 赠送对象分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class LicenseGiftTargetPageReqVO extends PageParam {

    @Schema(description = "客户类型：1 个人 2 企业；不传 = 全部", example = "1")
    private Integer targetType;

    @Schema(description = "关键字：会员昵称 / 手机号 / 企业名", example = "小王")
    private String keyword;

}
