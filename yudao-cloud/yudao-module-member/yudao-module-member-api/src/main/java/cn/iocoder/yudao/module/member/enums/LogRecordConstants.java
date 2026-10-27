package cn.iocoder.yudao.module.member.enums;

/**
 * 会员模块的操作日志文案（FR-318）。
 *
 * 企业创建、停用、角色变更、OWNER 转交、席位分配与撤销都必须留下「谁在什么时间做了什么」。
 */
public interface LogRecordConstants {

    // ======================= 企业 =======================

    String MEMBER_ORG_TYPE = "会员企业";

    String MEMBER_ORG_CREATE_SUB_TYPE = "创建企业";
    String MEMBER_ORG_CREATE_SUCCESS = "创建了企业【{{#name}}】";

    String MEMBER_ORG_UPDATE_STATUS_SUB_TYPE = "启用/停用企业";
    String MEMBER_ORG_UPDATE_STATUS_SUCCESS = "将企业【{{#id}}】的状态改为【{{#status}}】";

    String MEMBER_ORG_UPDATE_ROLE_SUB_TYPE = "调整企业成员角色";
    String MEMBER_ORG_UPDATE_ROLE_SUCCESS = "将企业【{{#orgId}}】成员【{{#memberId}}】的角色改为【{{#role}}】";

    String MEMBER_ORG_TRANSFER_OWNER_SUB_TYPE = "转交企业 OWNER";
    String MEMBER_ORG_TRANSFER_OWNER_SUCCESS = "将企业【{{#orgId}}】的 OWNER 转交给成员【{{#targetMemberId}}】";

    String MEMBER_ORG_SEAT_ASSIGN_SUB_TYPE = "分配企业席位";
    String MEMBER_ORG_SEAT_ASSIGN_SUCCESS = "为成员【{{#memberId}}】分配了企业【{{#orgId}}】的席位";

    String MEMBER_ORG_SEAT_REVOKE_SUB_TYPE = "撤销企业席位";
    String MEMBER_ORG_SEAT_REVOKE_SUCCESS = "撤销了成员【{{#memberId}}】在企业【{{#orgId}}】的席位";

}
