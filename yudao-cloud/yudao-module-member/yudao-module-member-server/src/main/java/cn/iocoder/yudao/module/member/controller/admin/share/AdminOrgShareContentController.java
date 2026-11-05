package cn.iocoder.yudao.module.member.controller.admin.share;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminOrgShareContentDetailRespVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminOrgShareContentPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminOrgShareContentRespVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminOrgShareOverviewRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.share.ShareContentService;
import cn.iocoder.yudao.module.member.service.share.ShareFolderService;
import cn.iocoder.yudao.module.member.service.share.ShareSpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.member.service.share.ShareConstants.ENTERPRISE_BYTES;

/**
 * 管理后台 - 企业共享内容（只读）
 *
 * 复用企业详情权限 member:org:query；运营只查看、预览、下载，不修改企业数据。
 */
@Tag(name = "管理后台 - MaxLabel 企业共享内容")
@RestController
@RequestMapping("/member/share-content")
@Validated
public class AdminOrgShareContentController {

    @Resource
    private ShareContentService contentService;
    @Resource
    private ShareFolderService folderService;
    @Resource
    private ShareSpaceService spaceService;
    @Resource
    private MemberUserMapper memberUserMapper;

    @GetMapping("/org-overview")
    @Operation(summary = "企业共享空间容量概览")
    @PreAuthorize("@ss.hasPermission('member:org:query')")
    public CommonResult<AdminOrgShareOverviewRespVO> getOverview(@RequestParam("orgId") Long orgId) {
        long used = spaceService.getEnterpriseUsedBytes(orgId);
        AdminOrgShareOverviewRespVO resp = new AdminOrgShareOverviewRespVO();
        resp.setQuotaBytes(ENTERPRISE_BYTES);
        resp.setUsedBytes(used);
        resp.setRemainingBytes(Math.max(0, ENTERPRISE_BYTES - used));
        return success(resp);
    }

    @GetMapping("/org-folders")
    @Operation(summary = "企业全部文件夹与用量")
    @PreAuthorize("@ss.hasPermission('member:org:query')")
    public CommonResult<List<cn.iocoder.yudao.module.member.service.share.dto.ShareFolderInfo>> getFolders(
            @RequestParam("orgId") Long orgId) {
        return success(folderService.getOrgListForAdmin(orgId));
    }

    @GetMapping("/org-page")
    @Operation(summary = "企业共享内容分页")
    @PreAuthorize("@ss.hasPermission('member:org:query')")
    public CommonResult<PageResult<AdminOrgShareContentRespVO>> getPage(
            @Valid AdminOrgShareContentPageReqVO reqVO) {
        PageResult<MaxLabelShareContentDO> page = contentService.getOrgPageForAdmin(
                reqVO.getOrgId(), reqVO.getFolderId(), reqVO.getKeyword(), reqVO.getContentType(), reqVO);
        Map<Long, String> folderNames = folderService.getOrgListForAdmin(reqVO.getOrgId()).stream()
                .collect(Collectors.toMap(
                        cn.iocoder.yudao.module.member.service.share.dto.ShareFolderInfo::getId,
                        cn.iocoder.yudao.module.member.service.share.dto.ShareFolderInfo::getName,
                        (left, right) -> left));
        List<AdminOrgShareContentRespVO> list = page.getList().stream()
                .map(content -> toResp(content, folderNames.get(content.getFolderId())))
                .toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "企业共享内容详情")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('member:org:query')")
    public CommonResult<AdminOrgShareContentDetailRespVO> get(@RequestParam("id") Long id) {
        MaxLabelShareContentDO content = contentService.getContentForAdmin(id);
        AdminOrgShareContentDetailRespVO resp = BeanUtils.toBean(content, AdminOrgShareContentDetailRespVO.class);
        resp.setUploaderMemberId(content.getOwnerMemberId());
        resp.setUploaderNickname(nickname(content.getOwnerMemberId()));
        resp.setDownloadUrl(contentService.getDownloadUrl(content));
        return success(resp);
    }

    private AdminOrgShareContentRespVO toResp(MaxLabelShareContentDO content, String folderName) {
        AdminOrgShareContentRespVO resp = BeanUtils.toBean(content, AdminOrgShareContentRespVO.class);
        resp.setFolderName(folderName);
        resp.setUploaderMemberId(content.getOwnerMemberId());
        resp.setUploaderNickname(nickname(content.getOwnerMemberId()));
        return resp;
    }

    private String nickname(Long memberId) {
        MemberUserDO user = memberUserMapper.selectById(memberId);
        if (user == null) {
            return "已退出成员";
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        String mobile = user.getMobile();
        if (mobile == null || mobile.length() < 7) {
            return "匿名会员";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

}
