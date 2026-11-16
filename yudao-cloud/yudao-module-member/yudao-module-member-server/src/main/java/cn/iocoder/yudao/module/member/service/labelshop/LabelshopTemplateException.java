package cn.iocoder.yudao.module.member.service.labelshop;

/**
 * 单个模板不可导入（正文为空、下载失败等），只影响该条。
 */
public class LabelshopTemplateException extends RuntimeException {

    public LabelshopTemplateException(String message) {
        super(message);
    }

}
