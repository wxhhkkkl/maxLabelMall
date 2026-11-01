package cn.iocoder.yudao.module.trade.enums.customization;

/**
 * 印刷定制的操作日志文案（014 / FR-380）
 */
public interface CustomizationLogRecordConstants {

    String CUSTOMIZATION_TYPE = "印刷定制";

    String MATERIAL_CREATE_SUB_TYPE = "创建材质";
    String MATERIAL_CREATE_SUCCESS = "创建了定制材质【{{#material.name}}】";

    String MATERIAL_UPDATE_SUB_TYPE = "修改材质";
    String MATERIAL_UPDATE_SUCCESS = "修改了定制材质【{{#material.name}}】";

    String MATERIAL_STATUS_SUB_TYPE = "修改材质状态";
    String MATERIAL_STATUS_SUCCESS = "将定制材质【{{#id}}】的状态改为 {{#status}}";

    String MATERIAL_DELETE_SUB_TYPE = "删除材质";
    String MATERIAL_DELETE_SUCCESS = "删除了定制材质【{{#id}}】";

    String CONFIG_SAVE_SUB_TYPE = "修改计价参数";
    String CONFIG_SAVE_SUCCESS = "修改了定制计价参数";

}
