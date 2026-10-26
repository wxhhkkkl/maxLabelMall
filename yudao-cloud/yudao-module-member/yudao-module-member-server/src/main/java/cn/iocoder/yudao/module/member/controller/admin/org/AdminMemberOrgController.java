package cn.iocoder.yudao.module.member.controller.admin.org;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.org.vo.MaxLabelOrgPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.org.vo.MaxLabelOrgRespVO;
import cn.iocoder.yudao.module.member.controller.admin.org.vo.MaxLabelOrgSaveReqVO;
import cn.iocoder.yudao.module.member.controller.admin.org.vo.MaxLabelOrgStatusReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
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

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_ORG_NOT_EXISTS;

@Tag(name = "管理后台 - MaxLabel 企业")
@RestController
@RequestMapping("/member/org")
@Validated
public class AdminMemberOrgController {

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
    @Operation(summary = "企业分页")
    @PreAuthorize("@ss.hasPermission('member:org:query')")
    public CommonResult<PageResult<MaxLabelOrgRespVO>> getPage(@Valid MaxLabelOrgPageReqVO reqVO) {
        PageResult<MaxLabelOrgDO> page = orgService.getOrgPage(reqVO.getName(), reqVO.getStatus(), reqVO);
        List<MaxLabelOrgRespVO> list = page.getList().stream().map(this::toRespVO).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "企业详情")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('member:org:query')")
    public CommonResult<MaxLabelOrgRespVO> get(@RequestParam("id") Long id) {
        MaxLabelOrgDO org = orgService.getOrg(id);
        if (org == null) {
            throw exception(MEMBER_ORG_NOT_EXISTS);
        }
        return success(toRespVO(org));
    }

    @PostMapping("/create")
    @Operation(summary = "创建企业")
    @PreAuthorize("@ss.hasPermission('member:org:create')")
    public CommonResult<Long> create(@Valid @RequestBody MaxLabelOrgSaveReqVO reqVO) {
        return success(orgService.createOrg(reqVO.getName(), reqVO.getOwnerMemberId(), reqVO.getRemark()));
    }

    @PutMapping("/update")
    @Operation(summary = "修改企业")
    @PreAuthorize("@ss.hasPermission('member:org:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody MaxLabelOrgSaveReqVO reqVO) {
        orgService.updateOrg(reqVO.getId(), reqVO.getName(), reqVO.getRemark());
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "启用/停用企业")
    @PreAuthorize("@ss.hasPermission('member:org:update')")
    public CommonResult<Boolean> updateStatus(@Valid @RequestBody MaxLabelOrgStatusReqVO reqVO) {
        orgService.updateStatus(reqVO.getId(), reqVO.getStatus());
        return success(true);
    }

    private MaxLabelOrgRespVO toRespVO(MaxLabelOrgDO org) {
        MaxLabelOrgRespVO vo = new MaxLabelOrgRespVO();
        vo.setId(org.getId());
        vo.setName(org.getName());
        vo.setOwnerMemberId(org.getOwnerMemberId());
        vo.setStatus(org.getStatus());
        vo.setCreateTime(org.getCreateTime());
        MemberUserDO owner = memberUserMapper.selectById(org.getOwnerMemberId());
        if (owner != null) {
            vo.setOwnerNickname(owner.getNickname());
            vo.setOwnerMobile(maskMobile(owner.getMobile()));
        }
        vo.setMemberCount(memberService.getMemberCount(org.getId()));
        vo.setSeatTotal(licenseService.getSeatTotal(org.getId()));
        vo.setSeatUsed(seatService.getActiveCount(org.getId()));
        LocalDateTime maxEnd = licenseService.getMaxEndTime(org.getId());
        vo.setLicenseEndDate(maxEnd == null ? null : maxEnd.toLocalDate().minusDays(1));
        return vo;
    }

    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

}
