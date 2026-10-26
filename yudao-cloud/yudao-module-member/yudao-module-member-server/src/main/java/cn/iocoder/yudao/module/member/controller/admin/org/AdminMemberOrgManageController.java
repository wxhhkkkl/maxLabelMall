package cn.iocoder.yudao.module.member.controller.admin.org;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.member.controller.admin.org.vo.*;
import cn.iocoder.yudao.module.member.controller.app.org.vo.AppMemberOrgMemberRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgLicenseDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgMemberDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgSeatDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgLicenseService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgMemberService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgSeatService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMap;

@Tag(name = "管理后台 - MaxLabel 企业成员与席位")
@RestController
@RequestMapping("/member/org")
@Validated
public class AdminMemberOrgManageController {

    private static final int OPERATOR_SYSTEM = 2;

    @Resource
    private MaxLabelOrgService orgService;
    @Resource
    private MaxLabelOrgMemberService memberService;
    @Resource
    private MaxLabelOrgSeatService seatService;
    @Resource
    private MaxLabelOrgLicenseService licenseService;
    @Resource
    private MemberUserMapper memberUserMapper;

    @GetMapping("/member/page")
    @Operation(summary = "企业成员分页（含席位状态）")
    @PreAuthorize("@ss.hasPermission('member:org:query')")
    public CommonResult<PageResult<AppMemberOrgMemberRespVO>> getMemberPage(
            @Valid MaxLabelOrgMemberPageReqVO reqVO) {
        PageResult<MaxLabelOrgMemberDO> page = memberService.getMemberPage(
                reqVO.getOrgId(), reqVO.getKeyword(), reqVO);
        if (CollUtil.isEmpty(page.getList())) {
            return success(PageResult.empty(page.getTotal()));
        }
        List<Long> memberIds = page.getList().stream()
                .map(MaxLabelOrgMemberDO::getMemberId).toList();
        Map<Long, MemberUserDO> users = convertMap(
                memberUserMapper.selectBatchIds(memberIds), MemberUserDO::getId);
        Map<Long, MaxLabelOrgSeatDO> seats = convertMap(
                seatService.getActiveList(reqVO.getOrgId()), MaxLabelOrgSeatDO::getMemberId);
        LocalDate licenseEnd = licenseEnd(reqVO.getOrgId());
        List<AppMemberOrgMemberRespVO> list = page.getList().stream().map(relation -> {
            AppMemberOrgMemberRespVO vo = new AppMemberOrgMemberRespVO();
            vo.setMemberId(relation.getMemberId());
            vo.setRole(relation.getRole());
            vo.setJoinedTime(relation.getJoinedTime());
            MemberUserDO user = users.get(relation.getMemberId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setMobileMasked(maskMobile(user.getMobile()));
            }
            boolean seatActive = seats.containsKey(relation.getMemberId()) && licenseEnd != null;
            vo.setSeatActive(seatActive);
            vo.setSeatEndDate(seatActive ? licenseEnd : null);
            return vo;
        }).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @PostMapping("/member/add")
    @Operation(summary = "按手机号加入成员")
    @PreAuthorize("@ss.hasPermission('member:org:member:add')")
    public CommonResult<Boolean> addMember(@Valid @RequestBody MaxLabelOrgMemberAddReqVO reqVO) {
        memberService.addMember(reqVO.getOrgId(), reqVO.getMobile(),
                OPERATOR_SYSTEM, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PostMapping("/member/remove")
    @Operation(summary = "移出成员")
    @PreAuthorize("@ss.hasPermission('member:org:member:remove')")
    public CommonResult<Boolean> removeMember(@Valid @RequestBody MaxLabelOrgMemberIdReqVO reqVO) {
        memberService.removeMember(reqVO.getOrgId(), reqVO.getMemberId(),
                OPERATOR_SYSTEM, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PutMapping("/member/update-role")
    @Operation(summary = "调整成员 ADMIN 角色")
    @PreAuthorize("@ss.hasPermission('member:org:member:update-role')")
    public CommonResult<Boolean> updateRole(@Valid @RequestBody MaxLabelOrgMemberRoleReqVO reqVO) {
        memberService.updateRole(reqVO.getOrgId(), reqVO.getMemberId(), reqVO.getRole(),
                OPERATOR_SYSTEM, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PostMapping("/owner/transfer")
    @Operation(summary = "运营代转 OWNER")
    @PreAuthorize("@ss.hasPermission('member:org:owner:transfer')")
    public CommonResult<Boolean> transferOwner(@Valid @RequestBody MaxLabelOrgMemberIdReqVO reqVO) {
        memberService.transferOwner(reqVO.getOrgId(), reqVO.getMemberId(),
                OPERATOR_SYSTEM, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PostMapping("/seat/assign")
    @Operation(summary = "运营代分配席位")
    @PreAuthorize("@ss.hasPermission('member:org:seat:assign')")
    public CommonResult<Boolean> assignSeat(@Valid @RequestBody MaxLabelOrgMemberIdReqVO reqVO) {
        seatService.assign(reqVO.getOrgId(), reqVO.getMemberId(),
                OPERATOR_SYSTEM, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PostMapping("/seat/revoke")
    @Operation(summary = "运营代撤销席位")
    @PreAuthorize("@ss.hasPermission('member:org:seat:revoke')")
    public CommonResult<Boolean> revokeSeat(@Valid @RequestBody MaxLabelOrgMemberIdReqVO reqVO) {
        seatService.revoke(reqVO.getOrgId(), reqVO.getMemberId(),
                OPERATOR_SYSTEM, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @GetMapping("/license/page")
    @Operation(summary = "企业授权批次分页")
    @PreAuthorize("@ss.hasPermission('member:org:license:query')")
    public CommonResult<PageResult<MaxLabelOrgLicenseRespVO>> getLicensePage(
            @Valid MaxLabelOrgLicensePageReqVO reqVO) {
        PageResult<MaxLabelOrgLicenseDO> page = licenseService.getPage(reqVO.getOrgId(), reqVO);
        List<MaxLabelOrgLicenseRespVO> list = page.getList().stream().map(row -> {
            MaxLabelOrgLicenseRespVO vo = new MaxLabelOrgLicenseRespVO();
            LocalDate endDate = row.getEndTime().toLocalDate().minusDays(1);
            vo.setId(row.getId());
            vo.setOrgId(row.getOrgId());
            vo.setPlanId(row.getPlanId());
            vo.setSpuId(row.getSpuId());
            vo.setSkuId(row.getSkuId());
            vo.setPeriodMonths(row.getPeriodMonths());
            vo.setSeatsTotal(row.getSeatsTotal());
            vo.setStartDate(row.getStartTime().toLocalDate());
            vo.setEndDate(endDate);
            vo.setRemainingDays(Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), endDate)));
            vo.setSourceOrderId(row.getSourceOrderId());
            vo.setSourceOrderNo(row.getSourceOrderNo());
            vo.setRefunded(row.getRefunded());
            vo.setFolded(row.getFoldedTime() != null);
            vo.setActive(row.getEndTime().isAfter(LocalDateTime.now())
                    && !Boolean.TRUE.equals(row.getRefunded())
                    && row.getFoldedTime() == null);
            vo.setCreateTime(row.getCreateTime());
            return vo;
        }).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/get-by-member")
    @Operation(summary = "获得会员所属企业（只读）")
    @Parameter(name = "memberId", required = true)
    @PreAuthorize("@ss.hasPermission('member:org:query')")
    public CommonResult<MaxLabelOrgMemberInfoRespVO> getByMember(@RequestParam("memberId") Long memberId) {
        MaxLabelOrgMemberInfoRespVO resp = new MaxLabelOrgMemberInfoRespVO();
        MemberOrgRespDTO org = orgService.getMemberOrg(memberId);
        if (org == null) {
            return success(resp);
        }
        resp.setOrgId(org.getOrgId());
        resp.setOrgName(org.getOrgName());
        resp.setRole(org.getRole());
        LocalDate licenseEnd = licenseEnd(org.getOrgId());
        boolean seatActive = seatService.getActiveByMemberId(memberId) != null
                && Integer.valueOf(0).equals(org.getStatus()) && licenseEnd != null;
        resp.setSeatActive(seatActive);
        resp.setSeatEndDate(seatActive ? licenseEnd : null);
        return success(resp);
    }

    private LocalDate licenseEnd(Long orgId) {
        LocalDateTime maxEnd = licenseService.getMaxEndTime(orgId);
        return maxEnd == null ? null : maxEnd.toLocalDate().minusDays(1);
    }

    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

}
