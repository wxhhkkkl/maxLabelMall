package cn.iocoder.yudao.module.member.service.license;

import java.util.Set;

/**
 * 赠送额度与档位的**唯一实现**（020 / FR-555 / T061）
 *
 * 前端置灰档位与后端校验 MUST 用同一套算法，否则会出现"前端能选、后端拒绝"。
 * 口径（2026-11-16 分析裁决）：`剩余月数 = ceil(剩余天数 / 30)`，
 * `最多还能送 = clamp(12 − 剩余月数, 0, 12)`。
 *
 * @author 芋道源码
 */
public final class LicenseGiftQuota {

    /** 赠送后可剩余的总时长上限（月） */
    public static final int MAX_TOTAL_MONTHS = 12;
    /** 单次可选的月数档位 */
    public static final Set<Integer> MONTH_TIERS = Set.of(1, 3, 6, 12);

    private LicenseGiftQuota() {
    }

    /**
     * 当前还能赠送的月数（0 表示不能送时长；上限 12）
     */
    public static int maxGiftMonths(long remainingDays) {
        int usedMonths = (int) Math.ceil(Math.max(0L, remainingDays) / 30.0);
        return Math.max(0, Math.min(MAX_TOTAL_MONTHS - usedMonths, MAX_TOTAL_MONTHS));
    }

    /**
     * 该档位当前是否可选（超出剩余额度即不可选）
     */
    public static boolean isTierAvailable(int months, long remainingDays) {
        return MONTH_TIERS.contains(months) && months <= maxGiftMonths(remainingDays);
    }

}
