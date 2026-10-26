package cn.iocoder.yudao.module.member.api.org.dto;

import lombok.Data;

/**
 * 会员所属企业 DTO（供 product / trade 判断企业管理员身份）
 */
@Data
public class MemberOrgRespDTO {

    /** 企业编号 */
    private Long orgId;
    /** 企业名称 */
    private String orgName;
    /** 会员在企业中的角色：1 OWNER 2 ADMIN 3 MEMBER */
    private Integer role;
    /** 企业状态：0 启用 1 停用 */
    private Integer status;

}
