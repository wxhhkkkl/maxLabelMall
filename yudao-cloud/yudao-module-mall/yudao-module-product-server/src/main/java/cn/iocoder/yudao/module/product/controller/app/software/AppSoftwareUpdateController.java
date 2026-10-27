package cn.iocoder.yudao.module.product.controller.app.software;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.controller.app.software.vo.AppSoftwareUpdateCheckRespVO;
import cn.iocoder.yudao.module.product.service.software.SoftwareUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 用户 App - 客户端更新（013 / Q89）
 *
 * 只下发元数据（几 KB），安装包始终由客户端直连对象存储下载。
 */
@Tag(name = "用户 App - 客户端更新")
@RestController
@RequestMapping("/software")
@Validated
public class AppSoftwareUpdateController {

    @Resource
    private SoftwareUpdateService updateService;

    @GetMapping("/update/check")
    @Operation(summary = "检查更新（服务端判定）")
    @PermitAll
    public CommonResult<AppSoftwareUpdateCheckRespVO> checkUpdate(
            @RequestParam(value = "softwareId", required = false) Long softwareId,
            @Parameter(name = "currentVersion", description = "客户端当前版本", required = true)
            @RequestParam("currentVersion") String currentVersion,
            @Parameter(name = "platform", description = "windows / macos / linux", required = true)
            @RequestParam("platform") String platform,
            @Parameter(name = "arch", description = "x64 / arm64 / x86", required = true)
            @RequestParam("arch") String arch) {
        return success(updateService.checkUpdate(softwareId, currentVersion, platform, arch));
    }

    @GetMapping(value = "/feed/{platform}/{arch}/latest.yml", produces = "text/yaml;charset=UTF-8")
    @Operation(summary = "生成 electron-updater 的 latest.yml")
    @PermitAll
    public String getLatestYml(@PathVariable("platform") String platform,
                               @PathVariable("arch") String arch,
                               HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        String yml = updateService.renderLatestYml(platform, arch);
        if (yml == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "";
        }
        return yml;
    }

}
