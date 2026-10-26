package cn.iocoder.yudao.module.member.service.org;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgLicenseGrantReqDTO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgLicenseDO;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgLicenseMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_ORG_RENEWAL_SEATS_TOO_FEW;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_SUBSCRIPTION_PERIOD_INVALID;

/**
 * MaxLabel 企业授权批次 Service 实现类
 *
 * 发放 = 企业行锁 + 幂等键。购买数量是**购买后的总席位数**；再次购买时把旧批次的
 * 剩余时长折算进新批次（Q78），旧批次打成「已折算」只留痕 —— 这样既不会出现
 * 「2 个席位但要等老批次到期才生效」，也不会让同一段时间被算两遍。
 */
@Service
@Validated
public class MaxLabelOrgLicenseServiceImpl implements MaxLabelOrgLicenseService {

    @Resource
    private MaxLabelOrgLicenseMapper licenseMapper;
    @Resource
    private MaxLabelOrgService orgService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaxLabelOrgLicenseDO grant(MemberOrgLicenseGrantReqDTO reqDTO) {
        validateGrant(reqDTO);
        MaxLabelOrgLicenseDO existing = licenseMapper.selectBySourceOrderItemId(reqDTO.getOrderItemId());
        if (existing != null) {
            return existing;
        }
        // 企业行锁：并发首购/复购串行化
        orgService.lockOrg(reqDTO.getOrgId());
        // 购买数量 = 购买后的总席位数，不能少于当前有效席位数（Q75）
        int currentSeats = getSeatTotal(reqDTO.getOrgId());
        if (currentSeats > 0 && reqDTO.getSeatsTotal() < currentSeats) {
            throw exception(MEMBER_ORG_RENEWAL_SEATS_TOO_FEW, currentSeats);
        }
        LocalDate today = LocalDate.now();
        // 剩余时长折算（Q78）：当前「席位数 × 剩余时长」+ 本次「席位数 × 购买周期」，
        // 再按本次席位数摊平。例：1 席还剩 10 个月，再买 2 席 × 1 年 → 2 席可用 17 个月。
        // ⚠️ 时长用「月 + 天」而不是纯天数：纯续费（本次席位数 = 当前席位数）时结果必须
        //    精确等于「原到期日 + 一个周期」，按天累加会在跨闰年时漂移 1 天（SC-107）。
        long grantedMonths = reqDTO.getPeriodMonths();
        long grantedDays = 0;
        if (currentSeats > 0) {
            LocalDateTime maxEnd = getMaxEndTime(reqDTO.getOrgId());
            long remainingMonths = 0;
            long remainingDays = 0;
            if (maxEnd != null) {
                LocalDate lastValidDay = maxEnd.toLocalDate().minusDays(1);
                remainingMonths = ChronoUnit.MONTHS.between(today, lastValidDay);
                remainingDays = ChronoUnit.DAYS.between(today.plusMonths(remainingMonths), lastValidDay);
            }
            long totalMonths = (long) currentSeats * remainingMonths
                    + (long) reqDTO.getSeatsTotal() * reqDTO.getPeriodMonths();
            long totalDays = (long) currentSeats * remainingDays;
            grantedMonths = totalMonths / reqDTO.getSeatsTotal();
            // 摊不平的整月按 30 天折进尾差；只影响零头，不改变整月对齐
            long leftoverMonths = totalMonths % reqDTO.getSeatsTotal();
            grantedDays = (leftoverMonths * 30 + totalDays) / reqDTO.getSeatsTotal();
        }
        // end_time 是"最后有效自然日 + 1 天的 00:00"
        LocalDate endDate = today.plusMonths(grantedMonths).plusDays(grantedDays);
        MaxLabelOrgLicenseDO license = MaxLabelOrgLicenseDO.builder()
                .orgId(reqDTO.getOrgId())
                .planId(reqDTO.getPlanId())
                .edition(reqDTO.getEdition())
                .spuId(reqDTO.getSpuId())
                .skuId(reqDTO.getSkuId())
                .periodMonths(reqDTO.getPeriodMonths())
                .seatsTotal(reqDTO.getSeatsTotal())
                .startTime(today.atStartOfDay())
                .endTime(endDate.plusDays(1).atStartOfDay())
                .sourceOrderId(reqDTO.getOrderId())
                .sourceOrderItemId(reqDTO.getOrderItemId())
                .sourceOrderNo(reqDTO.getOrderNo())
                .refunded(false)
                .build();
        try {
            licenseMapper.insert(license);
        } catch (DuplicateKeyException duplicate) {
            return licenseMapper.selectBySourceOrderItemId(reqDTO.getOrderItemId());
        }
        // 旧批次的剩余时长已经折进新批次：打成「已折算」，否则同一段时间会被算两遍。
        // ⚠️ 必须在插入之后、且排除新批次自己。
        if (currentSeats > 0) {
            licenseMapper.updateFoldedByOrgId(reqDTO.getOrgId(), license.getId(), LocalDateTime.now());
        }
        return license;
    }

    @Override
    public PageResult<MaxLabelOrgLicenseDO> getPage(Long orgId, PageParam pageParam) {
        return licenseMapper.selectPageByOrgId(orgId, pageParam);
    }

    @Override
    public int getSeatTotal(Long orgId) {
        // 当前席位 = 未过期且**未退款**批次中的最大席位数。
        // · 退款批次不能再提供可分配容量（否则退款后仍能分配席位）；
        // · 取 max 而不是按到期日取最新批次：续费规则要求席位只增不减，
        //   历史上“续费 1 席 < 原 2 席”的脏数据也不会把容量降下来。
        return getUnexpiredList(orgId).stream()
                .filter(row -> !Boolean.TRUE.equals(row.getRefunded()))
                .mapToInt(MaxLabelOrgLicenseDO::getSeatsTotal)
                .max()
                .orElse(0);
    }

    @Override
    public LocalDateTime getMaxEndTime(Long orgId) {
        return getUnexpiredList(orgId).stream()
                .map(MaxLabelOrgLicenseDO::getEndTime)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    @Override
    public List<MaxLabelOrgLicenseDO> getUnexpiredList(Long orgId) {
        // 退款批次只留痕：不参与容量、到期日与续费起点
        return licenseMapper.selectUnexpiredNonRefundedListByOrgId(orgId, LocalDateTime.now());
    }

    @Override
    public void markRefundedByOrder(Long orderId) {
        licenseMapper.updateRefundedByOrderId(orderId);
    }

    @Override
    public void markRefundedByOrderItem(Long orderItemId) {
        licenseMapper.updateRefundedByOrderItemId(orderItemId);
    }

    private void validateGrant(MemberOrgLicenseGrantReqDTO reqDTO) {
        if (reqDTO == null || reqDTO.getOrgId() == null || reqDTO.getPlanId() == null
                || reqDTO.getOrderId() == null || reqDTO.getOrderItemId() == null
                || reqDTO.getPeriodMonths() == null || reqDTO.getPeriodMonths() <= 0
                || reqDTO.getSeatsTotal() == null || reqDTO.getSeatsTotal() <= 0) {
            throw exception(MEMBER_SUBSCRIPTION_PERIOD_INVALID);
        }
    }

}
