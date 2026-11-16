package cn.iocoder.yudao.module.member.controller.admin.labelshop.vo;

import lombok.Data;

/**
 * 导入明细。
 */
@Data
public class AdminLabelshopImportItemRespVO {

    private Long id;
    private Integer sourceLibrary;
    private String sourceCategory;
    private String sourceName;
    private Integer targetScope;
    private String format;
    private Boolean editable;
    private Long sizeBytes;
    private Integer status;
    private String errorMessage;

}
