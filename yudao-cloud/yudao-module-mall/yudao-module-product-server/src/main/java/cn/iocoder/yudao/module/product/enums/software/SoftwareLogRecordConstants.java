package cn.iocoder.yudao.module.product.enums.software;

/**
 * 软件分发模块的操作日志文案（013 / FR-346）
 *
 * 软件与版本的创建、发布、下架、删除都必须留下「谁在什么时间做了什么」。
 */
public interface SoftwareLogRecordConstants {

    String SOFTWARE_TYPE = "软件分发";

    String SOFTWARE_CREATE_SUB_TYPE = "创建软件";
    String SOFTWARE_CREATE_SUCCESS = "创建了软件【{{#software.title}}】";

    String SOFTWARE_DELETE_SUB_TYPE = "删除软件";
    String SOFTWARE_DELETE_SUCCESS = "删除了软件【{{#id}}】";

    String SOFTWARE_VERSION_PUBLISH_SUB_TYPE = "发布软件版本";
    String SOFTWARE_VERSION_PUBLISH_SUCCESS = "发布了软件版本【{{#id}}】";

    String SOFTWARE_VERSION_UNPUBLISH_SUB_TYPE = "下架软件版本";
    String SOFTWARE_VERSION_UNPUBLISH_SUCCESS = "下架了软件版本【{{#id}}】";

    String SOFTWARE_VERSION_DELETE_SUB_TYPE = "删除软件版本";
    String SOFTWARE_VERSION_DELETE_SUCCESS = "删除了软件版本【{{#id}}】";

}
