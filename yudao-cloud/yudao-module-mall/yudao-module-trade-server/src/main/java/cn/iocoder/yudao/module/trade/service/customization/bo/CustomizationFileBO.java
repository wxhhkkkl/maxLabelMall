package cn.iocoder.yudao.module.trade.service.customization.bo;

import lombok.Data;

/**
 * 定制设计文件（014）
 */
@Data
public class CustomizationFileBO {

    /** 文件名 */
    private String name;
    /** 对象存储地址 */
    private String url;
    /** 文件大小（字节，前端上报） */
    private Long size;
    /** MIME 类型 */
    private String type;

}
