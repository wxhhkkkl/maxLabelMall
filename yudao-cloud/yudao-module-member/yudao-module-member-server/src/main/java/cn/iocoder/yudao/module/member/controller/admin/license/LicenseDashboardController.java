package cn.iocoder.yudao.module.member.controller.admin.license;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.license.MemberLicenseGrantDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.license.LicenseGrantService;
import cn.iocoder.yudao.module.member.service.license.LicenseQueryService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 管理后台 - 授权看板（020）
 *
 * 两个权限点分开授（R7）：
 * · `member:license:query` —— 看板与明细
 * · `member:license:gift` —— 赠送（会改变客户的商业权益，必须能单独收回）
 */
@Tag(name = "管理后台 - 授权看板")
@RestController
@RequestMapping("/member/license")
@Validated
public class LicenseDashboardController {

    @Resource
    private LicenseQueryService licenseQueryService;
    @Resource
    private LicenseGrantService licenseGrantService;
    @Resource
    private MemberUserMapper memberUserMapper;
    @Resource
    private MaxLabelOrgMapper orgMapper;
    @Resource
    private AdminUserApi adminUserApi;

    @GetMapping("/summary")
    @Operation(summary = "授权看板总览")
    @PreAuthorize("@ss.hasPermission('member:license:query')")
    public CommonResult<LicenseSummaryRespVO> getSummary() {
        return success(licenseQueryService.getSummary());
    }

    @GetMapping("/customer/page")
    @Operation(summary = "客户授权明细")
    @PreAuthorize("@ss.hasPermission('member:license:query')")
    public CommonResult<PageResult<LicenseCustomerRespVO>> getCustomerPage(
            @Valid LicenseCustomerPageReqVO reqVO) {
        return success(licenseQueryService.getCustomerPage(reqVO));
    }

    /**
     * 赠送对象分页：**全部**个人与企业，不限是否已有授权（Q162）
     *
     * 挂在 `member:license:gift` 下而不是 `query`：能看板不等于能送，
     * 但能送的人必须能选到对象，否则「送给新客户」这个动作做不出来。
     */
    @GetMapping("/gift-target/page")
    @Operation(summary = "赠送对象分页（全部个人与企业）")
    @PreAuthorize("@ss.hasPermission('member:license:gift')")
    public CommonResult<PageResult<LicenseGiftTargetRespVO>> getGiftTargetPage(
            @Valid LicenseGiftTargetPageReqVO reqVO) {
        return success(licenseQueryService.getGiftTargetPage(reqVO));
    }

    @PostMapping("/grant")
    @Operation(summary = "赠送授权")
    @PreAuthorize("@ss.hasPermission('member:license:gift')")
    public CommonResult<LicenseGrantRespVO> grant(@Valid @RequestBody LicenseGrantReqVO reqVO) {
        return success(licenseGrantService.grant(reqVO, getLoginUserId()));
    }

    @GetMapping("/grant/page")
    @Operation(summary = "赠送记录分页")
    @PreAuthorize("@ss.hasPermission('member:license:query')")
    public CommonResult<PageResult<LicenseGrantPageItemRespVO>> getGrantPage(
            @Valid LicenseGrantPageReqVO reqVO) {
        PageResult<MemberLicenseGrantDO> page = licenseGrantService.getGrantPage(reqVO);
        if (CollUtil.isEmpty(page.getList())) {
            return success(PageResult.empty());
        }
        // 批量回填对象名与操作人名（不逐条查）
        Collection<Long> memberIds = convertSet(page.getList().stream()
                .filter(row -> Objects.equals(MemberLicenseGrantDO.TARGET_TYPE_MEMBER, row.getTargetType()))
                .toList(), MemberLicenseGrantDO::getTargetId);
        Collection<Long> orgIds = convertSet(page.getList().stream()
                .filter(row -> Objects.equals(MemberLicenseGrantDO.TARGET_TYPE_ORG, row.getTargetType()))
                .toList(), MemberLicenseGrantDO::getTargetId);
        Map<Long, String> memberNameMap = memberUserMapper.selectBatchIds(memberIds).stream()
                .collect(Collectors.toMap(MemberUserDO::getId,
                        user -> user.getNickname() == null ? "" : user.getNickname(), (a, b) -> a));
        Map<Long, String> orgNameMap = orgMapper.selectBatchIds(orgIds).stream()
                .collect(Collectors.toMap(MaxLabelOrgDO::getId, MaxLabelOrgDO::getName, (a, b) -> a));
        Map<Long, String> operatorMap = adminUserApi.getUserList(
                        convertSet(page.getList(), MemberLicenseGrantDO::getOperatorId)).getCheckedData()
                .stream().collect(Collectors.toMap(AdminUserRespDTO::getId,
                        user -> user.getNickname() == null ? "" : user.getNickname(), (a, b) -> a));
        return success(new PageResult<>(
                page.getList().stream().map(row -> toGrantItem(row, memberNameMap, orgNameMap, operatorMap)).toList(),
                page.getTotal()));
    }

    private LicenseGrantPageItemRespVO toGrantItem(MemberLicenseGrantDO row,
                                                   Map<Long, String> memberNameMap,
                                                   Map<Long, String> orgNameMap,
                                                   Map<Long, String> operatorMap) {
        LicenseGrantPageItemRespVO vo = new LicenseGrantPageItemRespVO();
        vo.setId(row.getId());
        vo.setTargetType(row.getTargetType());
        vo.setTargetId(row.getTargetId());
        vo.setTargetName(Objects.equals(MemberLicenseGrantDO.TARGET_TYPE_MEMBER, row.getTargetType())
                ? memberNameMap.get(row.getTargetId()) : orgNameMap.get(row.getTargetId()));
        vo.setContentType(row.getContentType());
        vo.setEdition(row.getEdition());
        vo.setMonths(row.getMonths());
        vo.setSeats(row.getSeats());
        vo.setReason(row.getReason());
        vo.setBeforeEndTime(row.getBeforeEndTime() == null ? null : row.getBeforeEndTime().minusDays(1));
        vo.setAfterEndTime(row.getAfterEndTime() == null ? null : row.getAfterEndTime().minusDays(1));
        vo.setBeforeSeats(row.getBeforeSeats());
        vo.setAfterSeats(row.getAfterSeats());
        vo.setOperatorId(row.getOperatorId());
        vo.setOperatorName(operatorMap.get(row.getOperatorId()));
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

}
