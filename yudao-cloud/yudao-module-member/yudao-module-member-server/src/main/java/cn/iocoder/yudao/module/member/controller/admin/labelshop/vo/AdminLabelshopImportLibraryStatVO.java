package cn.iocoder.yudao.module.member.controller.admin.labelshop.vo;

import lombok.Data;

/**
 * 按来源库的导入统计。
 */
@Data
public class AdminLabelshopImportLibraryStatVO {

    /** 12 我的模板库 13 组模板库 1 企业/组织模板 */
    private Integer library;
    private Integer total = 0;
    private Integer success = 0;
    private Integer skip = 0;
    private Integer fail = 0;

}
