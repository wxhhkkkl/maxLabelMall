package cn.iocoder.yudao.module.member.dal.mysql.license;

import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseCustomerIdRow;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGiftTargetRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 授权看板的**统计与分页查询**（020 / T086）
 *
 * ⚠️ 本 Mapper 的存在理由：plan 的约束要求**统计必须一次查询出数**，
 * 不允许把整表取回后再在内存里分组过滤。这里每条 SQL 都把口径下推到数据库，
 * 服务层只做"把 id 装配成展示行"。
 *
 * 口径与 `data-model.md` §4 逐条对应：
 * · 有效 = 个人 `end_time > now`；企业 = 启用 + 有有效批次（未过期、未退款、未折算）
 * · 当年 = `create_time` 落在自然年（**不是** `start_time`：续费行的 start 可能是明年）
 * · 合计按客户去重（个人 1 人 / 企业 1 家各算 1）
 */
@Mapper
public interface LicenseStatisticsMapper {

    // ==================== 当前有效 ====================

    @Select("SELECT COUNT(DISTINCT member_id) FROM member_subscription "
            + "WHERE deleted = 0 AND end_time > #{now}")
    int countActivePersonalMembers(@Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM maxlabel_org o WHERE o.deleted = 0 AND o.status = 0 AND EXISTS ("
            + "  SELECT 1 FROM maxlabel_org_license l WHERE l.deleted = 0 AND l.org_id = o.id"
            + "    AND l.end_time > #{now} AND l.refunded = 0 AND l.folded_time IS NULL)")
    int countActiveOrgs(@Param("now") LocalDateTime now);

    /** 各有效企业「有效批次的 MAX(seats_total)」之和（与 getSeatTotal 同口径） */
    @Select("SELECT COALESCE(SUM(t.seats), 0) FROM ("
            + "  SELECT MAX(l.seats_total) AS seats FROM maxlabel_org_license l"
            + "  JOIN maxlabel_org o ON o.id = l.org_id AND o.deleted = 0 AND o.status = 0"
            + "  WHERE l.deleted = 0 AND l.end_time > #{now} AND l.refunded = 0 AND l.folded_time IS NULL"
            + "  GROUP BY l.org_id) t")
    int sumActiveOrgSeats(@Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM maxlabel_org_seat s"
            + " JOIN maxlabel_org o ON o.id = s.org_id AND o.deleted = 0 AND o.status = 0"
            + " WHERE s.deleted = 0 AND s.status = 0 AND EXISTS ("
            + "  SELECT 1 FROM maxlabel_org_license l WHERE l.deleted = 0 AND l.org_id = s.org_id"
            + "    AND l.end_time > #{now} AND l.refunded = 0 AND l.folded_time IS NULL)")
    int countActiveOrgSeats(@Param("now") LocalDateTime now);

    /** 席位会员数：有有效企业席位、但没有未过期个人订阅的会员（B 方案单独口径） */
    @Select("SELECT COUNT(DISTINCT st.member_id) FROM maxlabel_org_seat st"
            + " JOIN maxlabel_org o ON o.id = st.org_id AND o.deleted = 0 AND o.status = 0"
            + " WHERE st.deleted = 0 AND st.status = 0 AND EXISTS ("
            + "  SELECT 1 FROM maxlabel_org_license l WHERE l.deleted = 0 AND l.org_id = st.org_id"
            + "    AND l.end_time > #{now} AND l.refunded = 0 AND l.folded_time IS NULL)"
            + " AND NOT EXISTS (SELECT 1 FROM member_subscription s WHERE s.deleted = 0"
            + "    AND s.member_id = st.member_id AND s.end_time > #{now})")
    int countSeatOnlyMembers(@Param("now") LocalDateTime now);

    // ==================== 当年（新获得 / 延续） ====================

    /** 会员史上第一条订阅出现在今年 */
    @Select("SELECT COUNT(*) FROM (SELECT member_id FROM member_subscription WHERE deleted = 0"
            + "  GROUP BY member_id HAVING MIN(create_time) >= #{yearStart}) t")
    int countPersonalNew(@Param("yearStart") LocalDateTime yearStart);

    /** 今年有订阅、但更早年份已有过订阅 */
    @Select("SELECT COUNT(*) FROM (SELECT member_id FROM member_subscription WHERE deleted = 0"
            + "  GROUP BY member_id HAVING MIN(create_time) < #{yearStart} AND MAX(create_time) >= #{yearStart}) t")
    int countPersonalRenew(@Param("yearStart") LocalDateTime yearStart);

    /** 企业史上第一条批次出现在今年 */
    @Select("SELECT COUNT(*) FROM (SELECT org_id FROM maxlabel_org_license WHERE deleted = 0"
            + "  GROUP BY org_id HAVING MIN(create_time) >= #{yearStart}) t")
    int countOrgNew(@Param("yearStart") LocalDateTime yearStart);

    /** 今年有批次、但更早年份已有过批次 */
    @Select("SELECT COUNT(*) FROM (SELECT org_id FROM maxlabel_org_license WHERE deleted = 0"
            + "  GROUP BY org_id HAVING MIN(create_time) < #{yearStart} AND MAX(create_time) >= #{yearStart}) t")
    int countOrgRenewByBatch(@Param("yearStart") LocalDateTime yearStart);

    /** 今年只有「赠送」发生过延长（没有新批次）的企业 */
    @Select("SELECT COUNT(DISTINCT g.target_id) FROM member_license_grant g"
            + " JOIN (SELECT org_id, MAX(create_time) mx FROM maxlabel_org_license WHERE deleted = 0 GROUP BY org_id) b"
            + "   ON b.org_id = g.target_id"
            + " WHERE g.deleted = 0 AND g.target_type = 2 AND g.create_time >= #{yearStart} AND b.mx < #{yearStart}")
    int countOrgRenewByGrantOnly(@Param("yearStart") LocalDateTime yearStart);

    // ==================== 当年企业席位数（Q163） ====================

    /**
     * 当年「新获得」企业的当年新增批次席位总数。
     *
     * 口径：企业**史上第一批**发生在本年（= 新获得客户），其本年新增批次的 `seats_total` 求和。
     * 已退款、已折算的批次不计（它们不构成有效席位）。
     */
    @Select("SELECT COALESCE(SUM(l.seats_total), 0) FROM maxlabel_org_license l"
            + " JOIN (SELECT org_id, MIN(create_time) AS first_time FROM maxlabel_org_license"
            + "       WHERE deleted = 0 GROUP BY org_id) f ON f.org_id = l.org_id"
            + " WHERE l.deleted = 0 AND l.refunded = 0 AND l.folded_time IS NULL"
            + "   AND l.create_time >= #{yearStart} AND f.first_time >= #{yearStart}")
    int sumNewOrgSeatsByYear(@Param("yearStart") LocalDateTime yearStart);

    /**
     * 当年「延续」企业的当年新增批次席位总数。
     *
     * 与上面同口径，只是企业**史上第一批**早于本年（= 延续客户）。
     */
    @Select("SELECT COALESCE(SUM(l.seats_total), 0) FROM maxlabel_org_license l"
            + " JOIN (SELECT org_id, MIN(create_time) AS first_time FROM maxlabel_org_license"
            + "       WHERE deleted = 0 GROUP BY org_id) f ON f.org_id = l.org_id"
            + " WHERE l.deleted = 0 AND l.refunded = 0 AND l.folded_time IS NULL"
            + "   AND l.create_time >= #{yearStart} AND f.first_time < #{yearStart}")
    int sumRenewOrgSeatsByYear(@Param("yearStart") LocalDateTime yearStart);

    // ==================== 明细分页（SQL 侧筛选 + 排序 + 分页） ====================

    /**
     * 客户分页：个人与企业各一条子查询 UNION 后统一筛选、排序、分页
     *
     * `endDate` 是**最后有效日**（库里存的是次日 00:00，这里已 −1 天），
     * 状态筛选也基于它，避免"取回后再算一天之差"的两份口径。
     */
    @Select("<script>"
            + "SELECT target_type AS targetType, target_id AS targetId FROM ("
            + "  SELECT 1 AS target_type, u.id AS target_id, p.personal_end AS personal_end, o.org_end AS org_end,"
            + "         CASE WHEN p.personal_end IS NULL THEN o.org_end WHEN o.org_end IS NULL THEN p.personal_end ELSE GREATEST(p.personal_end, o.org_end) END AS end_date"
            + "  FROM member_user u"
            + "  LEFT JOIN (SELECT member_id, MAX(end_time) AS personal_end FROM member_subscription"
            + "             WHERE deleted = 0 GROUP BY member_id) p ON p.member_id = u.id"
            + "  LEFT JOIN (SELECT st.member_id AS member_id, MAX(l.end_time) AS org_end"
            + "             FROM maxlabel_org_seat st"
            + "             JOIN maxlabel_org o2 ON o2.id = st.org_id AND o2.deleted = 0 AND o2.status = 0"
            + "             JOIN maxlabel_org_license l ON l.org_id = st.org_id AND l.deleted = 0"
            + "               AND l.refunded = 0 AND l.folded_time IS NULL AND l.end_time > #{now}"
            + "             WHERE st.deleted = 0 AND st.status = 0 GROUP BY st.member_id) o ON o.member_id = u.id"
            + "  WHERE u.deleted = 0 AND (p.member_id IS NOT NULL OR o.member_id IS NOT NULL)"
            + "    <if test='keyword != null and keyword != \"\"'>"
            + "      AND (u.nickname LIKE CONCAT('%', #{keyword}, '%') OR u.mobile LIKE CONCAT('%', #{keyword}, '%'))"
            + "    </if>"
            + "  UNION ALL"
            + "  SELECT 2 AS target_type, org.id AS target_id, NULL AS personal_end, NULL AS org_end,"
            + "         MAX(l.end_time) AS end_date"
            + "  FROM maxlabel_org org JOIN maxlabel_org_license l ON l.org_id = org.id AND l.deleted = 0"
            + "  WHERE org.deleted = 0"
            + "    <if test='keyword != null and keyword != \"\"'>"
            + "      AND org.name LIKE CONCAT('%', #{keyword}, '%')"
            + "    </if>"
            + "  GROUP BY org.id, org.name"
            + ") t WHERE 1 = 1"
            + "  <if test='targetType != null'> AND t.target_type = #{targetType}</if>"
            + "  <if test='rightsType != null and rightsType == 1'> AND t.personal_end IS NOT NULL AND t.personal_end &gt; #{now}</if>"
            + "  <if test='rightsType != null and rightsType == 2'> AND (t.personal_end IS NULL OR t.personal_end &lt;= #{now}) AND t.org_end IS NOT NULL</if>"
            + "  <if test='status != null and status == 0'> AND t.end_date > #{now}</if>"
            + "  <if test='status != null and status == 1'> AND t.end_date &lt;= #{now}</if>"
            + " ORDER BY t.end_date DESC, t.target_type ASC, t.target_id DESC"
            + " LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<LicenseCustomerIdRow> selectCustomerPage(@Param("targetType") Integer targetType,
                                                  @Param("status") Integer status,
                                                  @Param("keyword") String keyword,
                                                  @Param("rightsType") Integer rightsType,
                                                  @Param("now") LocalDateTime now,
                                                  @Param("offset") int offset,
                                                  @Param("size") int size);

    /** 与上面**同一套筛选**的计数（保证明细与看板对账一致） */
    @Select("<script>"
            + "SELECT COUNT(*) FROM ("
            + "  SELECT 1 AS target_type, u.id AS target_id, p.personal_end AS personal_end, o.org_end AS org_end,"
            + "         CASE WHEN p.personal_end IS NULL THEN o.org_end WHEN o.org_end IS NULL THEN p.personal_end ELSE GREATEST(p.personal_end, o.org_end) END AS end_date"
            + "  FROM member_user u"
            + "  LEFT JOIN (SELECT member_id, MAX(end_time) AS personal_end FROM member_subscription"
            + "             WHERE deleted = 0 GROUP BY member_id) p ON p.member_id = u.id"
            + "  LEFT JOIN (SELECT st.member_id AS member_id, MAX(l.end_time) AS org_end"
            + "             FROM maxlabel_org_seat st"
            + "             JOIN maxlabel_org o2 ON o2.id = st.org_id AND o2.deleted = 0 AND o2.status = 0"
            + "             JOIN maxlabel_org_license l ON l.org_id = st.org_id AND l.deleted = 0"
            + "               AND l.refunded = 0 AND l.folded_time IS NULL AND l.end_time > #{now}"
            + "             WHERE st.deleted = 0 AND st.status = 0 GROUP BY st.member_id) o ON o.member_id = u.id"
            + "  WHERE u.deleted = 0 AND (p.member_id IS NOT NULL OR o.member_id IS NOT NULL)"
            + "    <if test='keyword != null and keyword != \"\"'>"
            + "      AND (u.nickname LIKE CONCAT('%', #{keyword}, '%') OR u.mobile LIKE CONCAT('%', #{keyword}, '%'))"
            + "    </if>"
            + "  UNION ALL"
            + "  SELECT 2 AS target_type, org.id AS target_id, NULL AS personal_end, NULL AS org_end,"
            + "         MAX(l.end_time) AS end_date"
            + "  FROM maxlabel_org org JOIN maxlabel_org_license l ON l.org_id = org.id AND l.deleted = 0"
            + "  WHERE org.deleted = 0"
            + "    <if test='keyword != null and keyword != \"\"'>"
            + "      AND org.name LIKE CONCAT('%', #{keyword}, '%')"
            + "    </if>"
            + "  GROUP BY org.id, org.name"
            + ") t WHERE 1 = 1"
            + "  <if test='targetType != null'> AND t.target_type = #{targetType}</if>"
            + "  <if test='rightsType != null and rightsType == 1'> AND t.personal_end IS NOT NULL AND t.personal_end &gt; #{now}</if>"
            + "  <if test='rightsType != null and rightsType == 2'> AND (t.personal_end IS NULL OR t.personal_end &lt;= #{now}) AND t.org_end IS NOT NULL</if>"
            + "  <if test='status != null and status == 0'> AND t.end_date > #{now}</if>"
            + "  <if test='status != null and status == 1'> AND t.end_date &lt;= #{now}</if>"
            + "</script>")
    long countCustomerPage(@Param("targetType") Integer targetType,
                           @Param("status") Integer status,
                           @Param("keyword") String keyword,
                           @Param("rightsType") Integer rightsType,
                           @Param("now") LocalDateTime now);

    // ==================== 赠送对象分页（全部个人与企业） ====================

    /**
     * 赠送对象分页：列出**全部**注册会员与全部企业，**不限是否已有授权**（Q162）。
     *
     * 与上面的客户明细不同，这里刻意不加「必须有授权」的 EXISTS 限制 ——
     * 赠送本来就要能送给从未购买过的客户（FR-548 最后一条）。
     * 个人到期取全部订阅的 MAX(end_time)（与赠送服务顺延口径一致）；
     * 企业只取有效批次（未过期、未退款、未折算，与 {@code grantToOrg} 一致）。
     *
     * ⚠️ 字符串列**必须 CAST AS CHAR 统一排序规则**：`member_user.nickname/mobile` 是
     * utf8mb4_general_ci，`maxlabel_org.name` 是 utf8mb4_unicode_ci，UNION 两个分支
     * 没有胜出者，线上会直接报 `Illegal mix of collations for operation 'UNION'`
     * （500 系统异常）。CAST 后两分支都用连接级的字符集/排序规则。
     * 不能用 `COLLATE` —— H2（单测库）不支持这个位置，单测会语法错。
     * H2 本身也复现不出 collation 冲突，**改这段 SQL 要拿真库跑一遍**。
     */
    @Select("<script>"
            + "SELECT t.target_type AS targetType, t.target_id AS targetId,"
            + "       t.target_name AS targetName, t.target_mobile AS targetMobile,"
            + "       t.status AS status, t.end_time AS endTime"
            + " FROM ("
            + "  SELECT 1 AS target_type, u.id AS target_id,"
            + "         CAST(u.nickname AS CHAR(255)) AS target_name,"
            + "         CAST(u.mobile AS CHAR(255)) AS target_mobile, u.status AS status,"
            + "         (SELECT MAX(s.end_time) FROM member_subscription s"
            + "            WHERE s.deleted = 0 AND s.member_id = u.id) AS end_time"
            + "  FROM member_user u WHERE u.deleted = 0"
            + "  UNION ALL"
            + "  SELECT 2 AS target_type, o.id AS target_id,"
            + "         CAST(o.name AS CHAR(255)) AS target_name,"
            + "         NULL AS target_mobile, o.status AS status,"
            + "         (SELECT MAX(l.end_time) FROM maxlabel_org_license l"
            + "            WHERE l.deleted = 0 AND l.org_id = o.id AND l.refunded = 0"
            + "              AND l.folded_time IS NULL AND l.end_time &gt; #{now}) AS end_time"
            + "  FROM maxlabel_org o WHERE o.deleted = 0"
            + ") t WHERE 1 = 1"
            + "  <if test='targetType != null'> AND t.target_type = #{targetType}</if>"
            + "  <if test='keyword != null and keyword != \"\"'>"
            + "    AND (t.target_name LIKE CONCAT('%', #{keyword}, '%')"
            + "         OR t.target_mobile LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + " ORDER BY t.target_type ASC, t.target_id DESC"
            + " LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<LicenseGiftTargetRow> selectGiftTargetPage(@Param("targetType") Integer targetType,
                                                    @Param("keyword") String keyword,
                                                    @Param("now") LocalDateTime now,
                                                    @Param("offset") int offset,
                                                    @Param("size") int size);

    /** 与上面**同一套筛选**的计数 */
    @Select("<script>"
            + "SELECT COUNT(*) FROM ("
            + "  SELECT 1 AS target_type, CAST(u.nickname AS CHAR(255)) AS target_name,"
            + "         CAST(u.mobile AS CHAR(255)) AS target_mobile"
            + "  FROM member_user u WHERE u.deleted = 0"
            + "  UNION ALL"
            + "  SELECT 2 AS target_type, CAST(o.name AS CHAR(255)) AS target_name,"
            + "         NULL AS target_mobile"
            + "  FROM maxlabel_org o WHERE o.deleted = 0"
            + ") t WHERE 1 = 1"
            + "  <if test='targetType != null'> AND t.target_type = #{targetType}</if>"
            + "  <if test='keyword != null and keyword != \"\"'>"
            + "    AND (t.target_name LIKE CONCAT('%', #{keyword}, '%')"
            + "         OR t.target_mobile LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "</script>")
    long countGiftTargetPage(@Param("targetType") Integer targetType,
                             @Param("keyword") String keyword);

}
