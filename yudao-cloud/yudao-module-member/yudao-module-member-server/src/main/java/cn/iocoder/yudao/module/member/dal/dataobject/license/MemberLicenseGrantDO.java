package cn.iocoder.yudao.module.member.dal.dataobject.license;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 授权赠送记录（020）
 *
 * **一次赠送一行，不可删除**；赠送内容不可改，撤回只追加 `revoke_*` 三列
 * （2026-11-19 / Q164 起允许撤回，推翻原 Q154「不可撤销」）。
 * 它同时回答两个问题：这笔授权是怎么来的（理由），以及赠送前后的到期日各是多少。
 *
 * ⚠️ 本表**只是留痕**：授权的实际变更落在 `member_subscription`（个人）或
 * `maxlabel_org_license`（企业批次）上，两者的 `license_grant_id` 指回本表。
 *
 * @author 芋道源码
 */
@TableName(value = "member_license_grant", autoResultMap = true)
@KeySequence("member_license_grant_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberLicenseGrantDO extends BaseDO {

    /** 对象类型：会员 */
    public static final int TARGET_TYPE_MEMBER = 1;
    /** 对象类型：企业 */
    public static final int TARGET_TYPE_ORG = 2;

    /** 内容类型：时长 */
    public static final int CONTENT_TYPE_MONTHS = 1;
    /** 内容类型：席位 */
    public static final int CONTENT_TYPE_SEATS = 2;

    /**
     * 赠送编号
     */
    private Long id;
    /**
     * 对象类型：1 会员 2 企业
     */
    private Integer targetType;
    /**
     * 对象编号：会员编号（member_user.id）或企业编号（maxlabel_org.id）
     */
    private Long targetId;
    /**
     * 内容类型：1 时长 2 席位
     */
    private Integer contentType;
    /**
     * 赠送版本快照：professional（个人）/ enterprise（企业），见 Q158
     */
    private String edition;
    /**
     * 赠送月数（contentType = 1 时必填；档位只能是 1 / 3 / 6 / 12）
     */
    private Integer months;
    /**
     * 赠送席位数（contentType = 2 时必填）
     */
    private Integer seats;
    /**
     * 赠送理由（必填，≤255 字）
     */
    private String reason;
    /**
     * 赠送前的到期时间；原本没有授权时为 null
     */
    private LocalDateTime beforeEndTime;
    /**
     * 赠送后的到期时间
     */
    private LocalDateTime afterEndTime;
    /**
     * 赠送前的席位数（企业席位赠送才有）
     */
    private Integer beforeSeats;
    /**
     * 赠送后的席位数（企业席位赠送才有）
     */
    private Integer afterSeats;
    /**
     * 企业赠送作用的授权批次编号（maxlabel_org_license.id）；个人赠送为 null
     *
     * 撤回要精确改回**同一批次**——靠"再算一遍 MAX(seats_total)"猜是不可靠的。
     * 由这笔赠送新建的批次可以只靠 {@code license_grant_id} 反查，但一并记下来更稳。
     */
    private Long licenseId;
    /**
     * 操作人编号，关联 system_users.id
     */
    private Long operatorId;
    /**
     * 撤回时间；非空 = 已撤回（2026-11-19 新增，推翻原「不可撤销」口径）
     */
    private LocalDateTime revokeTime;
    /**
     * 撤回人编号，关联 system_users.id
     */
    private Long revokeOperatorId;
    /**
     * 撤回理由（必填，≤255 字）
     */
    private String revokeReason;

}
