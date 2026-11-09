package cn.iocoder.yudao.module.member.controller.app.share;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareFolderMemberListRespVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareFolderMemberReqVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareFolderSaveReqVO;
import cn.iocoder.yudao.module.member.service.share.ShareFolderService;
import cn.iocoder.yudao.module.member.service.share.dto.ShareFolderInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - MaxLabel 企业文件夹")
@RestController
@RequestMapping("/member/share-folder")
@Validated
public class AppShareFolderController {

    @Resource
    private ShareFolderService folderService;

    @GetMapping("/list")
    @Operation(summary = "获得可见的企业文件夹")
    public CommonResult<List<ShareFolderInfo>> list() {
        return success(folderService.getVisibleList(SecurityFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/create")
    @Operation(summary = "创建企业文件夹")
    public CommonResult<Long> create(@Valid @RequestBody AppShareFolderSaveReqVO reqVO) {
        return success(folderService.create(SecurityFrameworkUtils.getLoginUserId(), reqVO.getName()));
    }

    @PutMapping("/update")
    @Operation(summary = "重命名企业文件夹")
    public CommonResult<Boolean> update(@Valid @RequestBody AppShareFolderSaveReqVO reqVO) {
        folderService.update(SecurityFrameworkUtils.getLoginUserId(), reqVO.getId(), reqVO.getName());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除空的企业文件夹")
    @Parameter(name = "id", required = true)
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        folderService.delete(SecurityFrameworkUtils.getLoginUserId(), id);
        return success(true);
    }

    @GetMapping("/member-list")
    @Operation(summary = "获得文件夹可见成员名单")
    @Parameter(name = "folderId", required = true)
    public CommonResult<AppShareFolderMemberListRespVO> getMemberList(@RequestParam("folderId") Long folderId) {
        AppShareFolderMemberListRespVO resp = new AppShareFolderMemberListRespVO();
        resp.setFolderId(folderId);
        resp.setMembers(folderService.getMemberList(SecurityFrameworkUtils.getLoginUserId(), folderId));
        return success(resp);
    }

    @PutMapping("/update-members")
    @Operation(summary = "保存文件夹可见成员名单")
    public CommonResult<Boolean> updateMembers(@Valid @RequestBody AppShareFolderMemberReqVO reqVO) {
        folderService.updateMembers(SecurityFrameworkUtils.getLoginUserId(), reqVO.getId(), reqVO.getMemberIds());
        return success(true);
    }

}
