package cn.iocoder.yudao.module.member.service.share;

/**
 * MaxLabel 共享内容空间常量。
 */
public final class ShareConstants {

    /** 空间类型：个人 */
    public static final int SCOPE_PERSONAL = 1;
    /** 空间类型：企业 */
    public static final int SCOPE_ENTERPRISE = 2;

    /** 内容类型 */
    public static final int TYPE_TEMPLATE = 1;
    public static final int TYPE_TXT = 2;
    public static final int TYPE_EXCEL = 3;
    public static final int TYPE_CSV = 4;
    public static final int TYPE_IMAGE = 5;

    /** 存储方式：文本正文 / 文件地址 */
    public static final int STORAGE_TEXT = 1;
    public static final int STORAGE_FILE = 2;

    /** 平台内容状态 */
    public static final int PLATFORM_ONLINE = 1;
    public static final int PLATFORM_OFFLINE = 0;

    public static final long MB = 1024L * 1024L;
    /** 标准版个人空间：10 MB */
    public static final long PERSONAL_STANDARD_BYTES = 10L * MB;
    /** 专业版 / 企业版个人空间：100 MB */
    public static final long PERSONAL_PAID_BYTES = 100L * MB;
    /** 企业共享池：300 MB */
    public static final long ENTERPRISE_BYTES = 300L * MB;
    /** 单文件默认上限：16 MB */
    public static final long DEFAULT_MAX_CONTENT_BYTES = 16L * MB;

    private ShareConstants() {
    }

}
