package cn.iocoder.yudao.module.member.controller.app.org;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.member.controller.app.org.vo.AppMemberOrgLicenseRespVO;
import cn.iocoder.yudao.module.member.controller.app.org.vo.AppMemberOrgRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgLicenseDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgSeatDO;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgLicenseService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgMemberService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgSeatService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - MaxLabel 企业中心")
@RestController
@RequestMapping("/member/org")
@Validated
public class AppMemberOrgController {

    @Resource
    private MaxLabelOrgService orgService;
    @Resource
    private MaxLabelOrgMemberService memberService;
    @Resource
    private MaxLabelOrgSeatService seatService;
    @Resource
    private MaxLabelOrgLicenseService licenseService;

    @GetMapping("/get")
    @Operation(summary = "获得我的企业")
    public CommonResult<AppMemberOrgRespVO> getMyOrg() {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        AppMemberOrgRespVO resp = new AppMemberOrgRespVO();
        MemberOrgRespDTO org = orgService.getMemberOrg(memberId);
        if (org == null) {
            return success(resp);
        }
        resp.setOrgId(org.getOrgId());
        resp.setOrgName(org.getOrgName());
        resp.setRole(org.getRole());
        resp.setStatus(org.getStatus());

        LocalDateTime maxEnd = licenseService.getMaxEndTime(org.getOrgId());
        LocalDate licenseEnd = maxEnd == null ? null : maxEnd.toLocalDate().minusDays(1);
        MaxLabelOrgSeatDO seat = seatService.getActiveByMemberId(memberId);
        boolean seatActive = seat != null && Integer.valueOf(0).equals(org.getStatus()) && licenseEnd != null;
        resp.setSeatActive(seatActive);
        resp.setSeatEndDate(seatActive ? licenseEnd : null);
        resp.setMemberCount((long) memberService.getMemberCount(org.getOrgId()));
        resp.setSeatTotal(licenseService.getSeatTotal(org.getOrgId()));
        resp.setSeatUsed(seatService.getActiveCount(org.getOrgId()));
        resp.setSeatAvailable(seatService.getAvailableSeats(org.getOrgId()));
        resp.setLicenseEndDate(licenseEnd);
        return success(resp);
    }

    @GetMapping("/license/page")
    @Operation(summary = "获得企业授权批次分页")
    public CommonResult<PageResult<AppMemberOrgLicenseRespVO>> getLicensePage(@Valid PageParam pageParam) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        MemberOrgRespDTO org = orgService.requireOrgAdmin(memberId);
        PageResult<MaxLabelOrgLicenseDO> page = licenseService.getPage(org.getOrgId(), pageParam);
        List<AppMemberOrgLicenseRespVO> list = page.getList().stream().map(row -> {
            AppMemberOrgLicenseRespVO vo = new AppMemberOrgLicenseRespVO();
            LocalDate endDate = row.getEndTime().toLocalDate().minusDays(1);
            vo.setId(row.getId());
            vo.setSkuId(row.getSkuId());
            vo.setPeriodMonths(row.getPeriodMonths());
            vo.setSeatsTotal(row.getSeatsTotal());
            vo.setStartDate(row.getStartTime().toLocalDate());
            vo.setEndDate(endDate);
            vo.setRemainingDays(Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), endDate)));
            vo.setSourceOrderNo(row.getSourceOrderNo());
            vo.setRefunded(row.getRefunded());
            vo.setFolded(row.getFoldedTime() != null);
            vo.setActive(row.getEndTime().isAfter(LocalDateTime.now())
                    && !Boolean.TRUE.equals(row.getRefunded())
                    && row.getFoldedTime() == null);
            return vo;
        }).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @PostMapping("/leave")
    @Operation(summary = "退出企业")
    public CommonResult<Boolean> leave() {
        memberService.leave(SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

}
