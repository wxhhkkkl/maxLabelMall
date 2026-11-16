package cn.iocoder.yudao.module.member.service.labelshop.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 一条待导入的来源模板。
 */
@Data
@Builder
public class LabelshopSourceItem {

    /** 来源库：12 我的模板库 13 组模板库 1 企业/组织模板 */
    private Integer sourceLibrary;
    /** LabelShop 模板 UUID */
    private String sourceRef;
    /** 来源分类路径 */
    private String sourceCategory;
    /** 来源模板名称 */
    private String sourceName;
    /** 格式：lsdx / cloud-json */
    private String format;
    /** 读取 / 下载时使用的 LabelShop @OWNTYPE（0 云模板、11 老账号、12 新账号、1 企业） */
    private Integer ownType;
    /** 目标空间：1 个人 2 企业 */
    private Integer targetScope;
    /** 目标归属：个人为 OWNER 会员编号，企业为 org_id */
    private Long targetOwnerId;

}
