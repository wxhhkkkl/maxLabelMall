package cn.iocoder.yudao.module.member.service.share;

import java.util.Locale;

/**
 * 按文件扩展名推断内容类型；用于上传时纠正前端未切换的类型。
 */
public final class ShareContentTypes {

    private ShareContentTypes() {
    }

    public static Integer infer(String name, Integer fallback) {
        if (name == null || !name.contains(".")) {
            return fallback;
        }
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "msdx" -> ShareConstants.TYPE_TEMPLATE;
            case "txt" -> ShareConstants.TYPE_TXT;
            case "xls", "xlsx" -> ShareConstants.TYPE_EXCEL;
            case "csv" -> ShareConstants.TYPE_CSV;
            case "png", "jpg", "jpeg", "gif", "bmp", "webp" -> ShareConstants.TYPE_IMAGE;
            default -> fallback;
        };
    }

}
