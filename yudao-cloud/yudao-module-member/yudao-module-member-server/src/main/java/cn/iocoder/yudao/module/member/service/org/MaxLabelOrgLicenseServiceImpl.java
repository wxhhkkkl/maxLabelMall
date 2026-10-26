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
import java.util.Comparator;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_SUBSCRIPTION_PERIOD_INVALID;

/**
 * MaxLabel 企业授权批次 Service 实现类
 *
 * 发放 = 企业行锁 + 幂等键；周期取 plan.periodMonths，数量只决定席位数。
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
        // 企业行锁：并发首购/续费串行化
        orgService.lockOrg(reqDTO.getOrgId());
        LocalDate today = LocalDate.now();
        // end_time 是"最后有效日 + 1 天的 00:00"；续费应从**原到期日**（最后有效日）接续，
        // 否则每续一次会多出 1 天（SC-107 要求误差 0 天）。
        LocalDate startDate = licenseMapper.selectListByOrgId(reqDTO.getOrgId()).stream()
                .map(MaxLabelOrgLicenseDO::getEndTime)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(endTime -> {
                    LocalDate lastValidDay = endTime.toLocalDate().minusDays(1);
                    return lastValidDay.isAfter(today) ? lastValidDay : today;
                })
                .orElse(today);
        LocalDate endDate = startDate.plusMonths(reqDTO.getPeriodMonths());
        MaxLabelOrgLicenseDO license = MaxLabelOrgLicenseDO.builder()
                .orgId(reqDTO.getOrgId())
                .planId(reqDTO.getPlanId())
                .spuId(reqDTO.getSpuId())
                .skuId(reqDTO.getSkuId())
                .periodMonths(reqDTO.getPeriodMonths())
                .seatsTotal(reqDTO.getSeatsTotal())
                .startTime(startDate.atStartOfDay())
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
        return license;
    }

    @Override
    public PageResult<MaxLabelOrgLicenseDO> getPage(Long orgId, PageParam pageParam) {
        return licenseMapper.selectPageByOrgId(orgId, pageParam);
    }

    @Override
    public int getSeatTotal(Long orgId) {
        return getUnexpiredList(orgId).stream()
                .mapToInt(MaxLabelOrgLicenseDO::getSeatsTotal)
                .sum();
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
        return licenseMapper.selectUnexpiredListByOrgId(orgId, LocalDateTime.now());
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
