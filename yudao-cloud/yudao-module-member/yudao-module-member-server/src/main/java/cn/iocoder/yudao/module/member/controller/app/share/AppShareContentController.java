package cn.iocoder.yudao.module.member.controller.app.share;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentDetailRespVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentPageReqVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentRespVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentSaveReqVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentUploadReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.share.ShareContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - MaxLabel 共享内容")
@RestController
@RequestMapping("/member/share-content")
@Validated
public class AppShareContentController {

    @Resource
    private ShareContentService contentService;
    @Resource
    private MemberUserMapper memberUserMapper;

    @GetMapping("/page")
    @Operation(summary = "获得共享内容分页")
    public CommonResult<PageResult<AppShareContentRespVO>> getPage(@Valid AppShareContentPageReqVO reqVO) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        PageResult<MaxLabelShareContentDO> page = contentService.getPage(memberId, reqVO);
        List<AppShareContentRespVO> list = page.getList().stream()
                .map(content -> toResp(memberId, content)).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/mine")
    @Operation(summary = "获得我上传到企业的内容（跨文件夹）")
    public CommonResult<PageResult<AppShareContentRespVO>> getMine(@Valid PageParam pageParam) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        PageResult<MaxLabelShareContentDO> page = contentService.getMyEnterprisePage(memberId, pageParam);
        List<AppShareContentRespVO> list = page.getList().stream()
                .map(content -> toResp(memberId, content)).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得共享内容详情")
    @Parameter(name = "id", required = true)
    public CommonResult<AppShareContentDetailRespVO> get(@RequestParam("id") Long id) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        MaxLabelShareContentDO content = contentService.getContent(memberId, id);
        AppShareContentDetailRespVO resp = toDetail(memberId, content);
        return success(resp);
    }

    @PostMapping("/create")
    @Operation(summary = "创建共享内容（文本正文）")
    public CommonResult<Long> create(@Valid @RequestBody AppShareContentSaveReqVO reqVO) {
        return success(contentService.create(SecurityFrameworkUtils.getLoginUserId(), reqVO));
    }

    @PostMapping("/upload")
    @Operation(summary = "上传共享内容（文件）")
    public CommonResult<Long> upload(@Valid AppShareContentUploadReqVO reqVO) throws Exception {
        return success(contentService.upload(SecurityFrameworkUtils.getLoginUserId(), reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新共享内容")
    public CommonResult<Boolean> update(@Valid @RequestBody AppShareContentSaveReqVO reqVO) {
        contentService.update(SecurityFrameworkUtils.getLoginUserId(), reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除共享内容")
    @Parameter(name = "id", required = true)
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        contentService.delete(SecurityFrameworkUtils.getLoginUserId(), id);
        return success(true);
    }

    private AppShareContentRespVO toResp(Long memberId, MaxLabelShareContentDO content) {
        AppShareContentRespVO resp = BeanUtils.toBean(content, AppShareContentRespVO.class);
        resp.setUploaderMemberId(content.getOwnerMemberId());
        resp.setUploaderNickname(nickname(content.getOwnerMemberId()));
        resp.setCanDelete(contentService.canDelete(memberId, content));
        return resp;
    }

    private AppShareContentDetailRespVO toDetail(Long memberId, MaxLabelShareContentDO content) {
        AppShareContentDetailRespVO resp = BeanUtils.toBean(content, AppShareContentDetailRespVO.class);
        resp.setUploaderMemberId(content.getOwnerMemberId());
        resp.setUploaderNickname(nickname(content.getOwnerMemberId()));
        resp.setCanDelete(contentService.canDelete(memberId, content));
        resp.setDownloadUrl(contentService.getDownloadUrl(content));
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
