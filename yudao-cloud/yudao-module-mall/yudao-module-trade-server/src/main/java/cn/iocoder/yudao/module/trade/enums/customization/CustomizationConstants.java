package cn.iocoder.yudao.module.trade.enums.customization;

import java.util.Set;

/**
 * 印刷定制（014）常量。
 *
 * ⚠️ 隐藏 SPU/SKU 由 `sql/mysql/027-print-customization.sql` 预置，**不要删除** ——
 * `trade_order_item.spu_id / sku_id` 是 NOT NULL，且库存处理器会按 SKU 扣减。
 */
public final class CustomizationConstants {

    private CustomizationConstants() {
    }

    /** 隐藏 SPU「印刷定制服务（系统）」 */
    public static final Long HIDDEN_SPU_ID = 990001L;
    /** 隐藏 SKU */
    public static final Long HIDDEN_SKU_ID = 990002L;

    /** 设计文件数量：1~5（Q92） */
    public static final int FILE_MIN_COUNT = 1;
    public static final int FILE_MAX_COUNT = 5;
    /** 单文件上限 16MB，与 `spring.servlet.multipart.max-file-size` 一致（Q106） */
    public static final long FILE_MAX_SIZE = 16L * 1024 * 1024;
    /** 允许的扩展名（小写，不带点） */
    public static final Set<String> FILE_ALLOWED_EXTENSIONS = Set.of("pdf", "ai", "cdr", "jpg", "jpeg", "png");

    /** 文件 URL 的域名白名单；可由 `yudao.customization.file-domain` 覆盖 */
    public static final String DEFAULT_FILE_DOMAIN = "https://img.yuwangchenfa.com";

    /** 60 秒幂等键（Q99） */
    public static final String SUBMIT_LOCK_KEY_PREFIX = "trade:customization:submit:";
    public static final long SUBMIT_LOCK_SECONDS = 60L;

    /** 面积 / 金额的精度 */
    public static final int AREA_SCALE = 4;
}
