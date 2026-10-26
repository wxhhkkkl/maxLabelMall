package cn.iocoder.yudao.module.member.service.org;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgSeatDO;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgMemberMapper;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgSeatMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;

/**
 * MaxLabel 企业席位 Service 实现类
 *
 * 容量 = max(0, 未过期批次席位和 - 有效席位)；企业行锁保证并发不超发。
 */
@Service
@Validated
public class MaxLabelOrgSeatServiceImpl implements MaxLabelOrgSeatService {

    @Resource
    private MaxLabelOrgSeatMapper seatMapper;
    @Resource
    private MaxLabelOrgMemberMapper orgMemberMapper;
    @Resource
    private MaxLabelOrgService orgService;
    @Resource
    private MaxLabelOrgMemberService orgMemberService;
    @Resource
    private MaxLabelOrgLicenseService licenseService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long orgId, Long memberId, Integer operatorType, Long operatorId) {
        // 企业行锁：容量校验与写入串行化
        orgService.lockOrg(orgId);
        orgMemberService.validateOperator(orgId, operatorType, operatorId, false);
        if (orgMemberMapper.selectByOrgIdAndMemberId(orgId, memberId) == null) {
            throw exception(MEMBER_ORG_NOT_MEMBER);
        }
        MaxLabelOrgSeatDO seat = seatMapper.selectByOrgIdAndMemberId(orgId, memberId);
        if (seat != null && Integer.valueOf(0).equals(seat.getStatus())) {
            throw exception(MEMBER_ORG_SEAT_ALREADY);
        }
        if (getAvailableSeats(orgId) <= 0) {
            throw exception(MEMBER_ORG_SEAT_FULL);
        }
        LocalDateTime now = LocalDateTime.now();
        if (seat == null) {
            seatMapper.insert(MaxLabelOrgSeatDO.builder()
                    .orgId(orgId)
                    .memberId(memberId)
                    .status(0)
                    .operatorType(operatorType)
                    .operatorId(operatorId)
                    .assignedTime(now)
                    .build());
        } else {
            seatMapper.updateById(new MaxLabelOrgSeatDO().setId(seat.getId())
                    .setStatus(0).setOperatorType(operatorType).setOperatorId(operatorId)
                    .setAssignedTime(now).setRevokedTime(null));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long orgId, Long memberId, Integer operatorType, Long operatorId) {
        orgService.lockOrg(orgId);
        orgMemberService.validateOperator(orgId, operatorType, operatorId, false);
        MaxLabelOrgSeatDO seat = seatMapper.selectByOrgIdAndMemberId(orgId, memberId);
        if (seat == null || !Integer.valueOf(0).equals(seat.getStatus())) {
            throw exception(MEMBER_ORG_SEAT_NOT_EXISTS);
        }
        seatMapper.updateById(new MaxLabelOrgSeatDO().setId(seat.getId())
                .setStatus(1).setRevokedTime(LocalDateTime.now())
                .setOperatorType(operatorType).setOperatorId(operatorId));
    }

    @Override
    public MaxLabelOrgSeatDO getActiveByMemberId(Long memberId) {
        return seatMapper.selectActiveByMemberId(memberId);
    }

    @Override
    public java.util.List<MaxLabelOrgSeatDO> getActiveList(Long orgId) {
        return seatMapper.selectActiveListByOrgId(orgId);
    }

    @Override
    public int getActiveCount(Long orgId) {
        return seatMapper.selectActiveCountByOrgId(orgId);
    }

    @Override
    public int getAvailableSeats(Long orgId) {
        return Math.max(0, licenseService.getSeatTotal(orgId) - getActiveCount(orgId));
    }

    @Override
    public PageResult<MaxLabelOrgSeatDO> getPage(Long orgId, PageParam pageParam) {
        return seatMapper.selectPageByOrgId(orgId, pageParam);
    }

}
