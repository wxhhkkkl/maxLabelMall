package cn.iocoder.yudao.module.member.controller.admin.license.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 授权看板 - 总览
 *
 * ⚠️ 当年的三个数字是**分开的**（Q155）：拉新与续费是两种业务含义，合并会被误读成"全是新客"。
 */
@Data
public class LicenseSummaryRespVO {

    @Schema(description = "个人有效授权数（distinct 会员）", example = "128")
    private Integer personalActiveCount;

    @Schema(description = "席位会员数（有有效企业席位、但没有未过期个人订阅的会员）", example = "23")
    private Integer personalSeatOnlyCount;

    @Schema(description = "企业有效授权数", example = "12")
    private Integer orgActiveCount;

    @Schema(description = "有效企业已分配席位数", example = "86")
    private Integer orgSeatActiveCount;

    @Schema(description = "有效企业席位总数", example = "120")
    private Integer orgSeatTotalCount;

    @Schema(description = "当年「新获得授权」客户数", example = "34")
    private Integer yearNewCount;

    @Schema(description = "当年「延续授权」客户数", example = "21")
    private Integer yearRenewCount;

    @Schema(description = "当年合计（按客户去重）", example = "49")
    private Integer yearTotalCount;

    @Schema(description = "当年新获得授权中的个人会员数（Q163）", example = "30")
    private Integer yearNewPersonalCount;

    @Schema(description = "当年新获得授权中的企业席位数（当年新增批次的 seats_total 之和，Q163）", example = "40")
    private Integer yearNewOrgSeatCount;

    @Schema(description = "当年延续授权中的个人会员数（Q163）", example = "18")
    private Integer yearRenewPersonalCount;

    @Schema(description = "当年延续授权中的企业席位数（当年新增批次的 seats_total 之和，Q163）", example = "15")
    private Integer yearRenewOrgSeatCount;

    @Schema(description = "统计所属自然年", example = "2026")
    private Integer year;

}
