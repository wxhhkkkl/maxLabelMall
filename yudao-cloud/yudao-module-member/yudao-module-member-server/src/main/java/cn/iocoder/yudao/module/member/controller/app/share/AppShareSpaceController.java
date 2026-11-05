package cn.iocoder.yudao.module.member.controller.app.share;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.controller.app.share.vo.ShareSpaceOverviewRespVO;
import cn.iocoder.yudao.module.member.service.share.ShareSpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - MaxLabel 共享空间")
@RestController
@RequestMapping("/member/share-space")
@Validated
public class AppShareSpaceController {

    @Resource
    private ShareSpaceService spaceService;

    @GetMapping("/overview")
    @Operation(summary = "获得个人 / 企业共享空间概览")
    public CommonResult<ShareSpaceOverviewRespVO> getOverview() {
        return success(spaceService.getOverview(SecurityFrameworkUtils.getLoginUserId()));
    }

}
