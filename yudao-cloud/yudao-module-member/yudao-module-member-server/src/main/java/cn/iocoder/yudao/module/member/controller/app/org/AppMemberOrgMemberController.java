package cn.iocoder.yudao.module.member.controller.app.org;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.member.controller.app.org.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgMemberDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgSeatDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgLicenseService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgMemberService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgSeatService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMap;

@Tag(name = "用户 App - MaxLabel 企业成员")
@RestController
@RequestMapping("/member/org/member")
@Validated
public class AppMemberOrgMemberController {

    private static final int OPERATOR_MEMBER = 1;

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

    @GetMapping("/page")
    @Operation(summary = "企业成员分页（含席位状态）")
    public CommonResult<PageResult<AppMemberOrgMemberRespVO>> getMemberPage(
            @Valid AppMemberOrgMemberPageReqVO reqVO) {
        Long operatorId = SecurityFrameworkUtils.getLoginUserId();
        MemberOrgRespDTO org = orgService.requireOrgAdmin(operatorId);
        PageResult<MaxLabelOrgMemberDO> page = memberService.getMemberPage(
                org.getOrgId(), reqVO.getKeyword(), reqVO);
        if (CollUtil.isEmpty(page.getList())) {
            return success(PageResult.empty(page.getTotal()));
        }
        List<Long> memberIds = page.getList().stream()
                .map(MaxLabelOrgMemberDO::getMemberId).toList();
        Map<Long, MemberUserDO> users = convertMap(
                memberUserMapper.selectBatchIds(memberIds), MemberUserDO::getId);
        Map<Long, MaxLabelOrgSeatDO> seats = convertMap(
                seatService.getActiveList(org.getOrgId()), MaxLabelOrgSeatDO::getMemberId);
        LocalDateTime maxEnd = licenseService.getMaxEndTime(org.getOrgId());
        LocalDate licenseEnd = maxEnd == null ? null : maxEnd.toLocalDate().minusDays(1);

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

    @PostMapping("/add")
    @Operation(summary = "按手机号加入成员")
    public CommonResult<Boolean> addMember(@Valid @RequestBody AppMemberOrgMemberAddReqVO reqVO) {
        Long operatorId = SecurityFrameworkUtils.getLoginUserId();
        MemberOrgRespDTO org = orgService.requireOrgAdmin(operatorId);
        memberService.addMember(org.getOrgId(), reqVO.getMobile(), OPERATOR_MEMBER, operatorId);
        return success(true);
    }

    @PostMapping("/remove")
    @Operation(summary = "移出成员")
    public CommonResult<Boolean> removeMember(@Valid @RequestBody AppMemberOrgMemberIdReqVO reqVO) {
        Long operatorId = SecurityFrameworkUtils.getLoginUserId();
        MemberOrgRespDTO org = orgService.requireOrgAdmin(operatorId);
        memberService.removeMember(org.getOrgId(), reqVO.getMemberId(), OPERATOR_MEMBER, operatorId);
        return success(true);
    }

    @PutMapping("/update-role")
    @Operation(summary = "调整成员角色（仅 OWNER）")
    public CommonResult<Boolean> updateRole(@Valid @RequestBody AppMemberOrgMemberRoleReqVO reqVO) {
        Long operatorId = SecurityFrameworkUtils.getLoginUserId();
        MemberOrgRespDTO org = orgService.requireOrgAdmin(operatorId);
        memberService.updateRole(org.getOrgId(), reqVO.getMemberId(), reqVO.getRole(),
                OPERATOR_MEMBER, operatorId);
        return success(true);
    }

    @PostMapping("/owner/transfer")
    @Operation(summary = "转交 OWNER（仅 OWNER）")
    public CommonResult<Boolean> transferOwner(@Valid @RequestBody AppMemberOrgMemberIdReqVO reqVO) {
        Long operatorId = SecurityFrameworkUtils.getLoginUserId();
        MemberOrgRespDTO org = orgService.requireOrgAdmin(operatorId);
        memberService.transferOwner(org.getOrgId(), reqVO.getMemberId(), OPERATOR_MEMBER, operatorId);
        return success(true);
    }

    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

}
