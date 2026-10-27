package cn.iocoder.yudao.module.product.service.software;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.product.controller.app.software.vo.AppSoftwareUpdateCheckRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareDO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareVersionDO;
import cn.iocoder.yudao.module.product.enums.software.SoftwareArchEnum;
import cn.iocoder.yudao.module.product.enums.software.SoftwarePlatformEnum;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 客户端更新判定 Service 实现类（013 / Q89 / Q90）
 */
@Service
@Validated
public class SoftwareUpdateServiceImpl implements SoftwareUpdateService {

    private static final DateTimeFormatter YML_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("UTC"));

    /** 对外站点根地址；feedUrl 与 latest.yml 都挂在它下面 */
    @Value("${maxlabel.software.public-base-url:https://new.yuwangchenfa.com}")
    private String publicBaseUrl;

    @Resource
    private SoftwareService softwareService;
    @Resource
    private SoftwareVersionService versionService;

    @Override
    public AppSoftwareUpdateCheckRespVO checkUpdate(Long softwareId, String currentVersion,
                                                    String platform, String arch) {
        SoftwareVersionDO target = resolveCurrent(softwareId, platform, arch);
        if (target == null) {
            return AppSoftwareUpdateCheckRespVO.noUpdate();
        }
        // 客户端版本 ≥ 当前发布：视为已是最新，不返回 feedUrl（Q90，防降级）。
        // 版本号非法也走这条分支 —— 无法判定就不下发任何文件（Edge Case）。
        if (!isNewer(currentVersion, target.getVersion())) {
            return AppSoftwareUpdateCheckRespVO.noUpdate();
        }
        AppSoftwareUpdateCheckRespVO vo = new AppSoftwareUpdateCheckRespVO();
        vo.setHasUpdate(true);
        vo.setLatestVersion(target.getVersion());
        vo.setSha512(target.getSha512());
        vo.setSize(target.getFileSize());
        vo.setReleaseNotes(target.getReleaseNotes());
        vo.setFeedUrl(buildFeedUrl(platform, arch));
        return vo;
    }

    @Override
    public String renderLatestYml(String platform, String arch) {
        SoftwareVersionDO target = resolveCurrent(null, platform, arch);
        if (target == null) {
            return null;
        }
        String releaseDate = target.getReleaseTime() != null
                ? YML_DATE.format(target.getReleaseTime().atZone(ZoneId.systemDefault()))
                : "";
        return "version: " + target.getVersion() + "\n"
                + "files:\n"
                + "  - url: " + target.getFileUrl() + "\n"
                + "    sha512: " + target.getSha512() + "\n"
                + "    size: " + target.getFileSize() + "\n"
                + "path: " + target.getFileUrl() + "\n"
                + "sha512: " + target.getSha512() + "\n"
                + (releaseDate.isEmpty() ? "" : "releaseDate: '" + releaseDate + "'\n")
                + (StrUtil.isEmpty(target.getReleaseNotes()) ? ""
                    : "releaseNotes: " + target.getReleaseNotes().replace("\n", " ") + "\n");
    }

    /** 定位「该软件 × 平台 × 架构」的当前发布；任何前置条件不满足都返回 null */
    private SoftwareVersionDO resolveCurrent(Long softwareId, String platform, String arch) {
        if (SoftwarePlatformEnum.parse(platform) == null || SoftwareArchEnum.parse(arch) == null) {
            return null;
        }
        SoftwareDO software = softwareId != null
                ? softwareService.getEnabledDesktopSoftware(softwareId)
                : softwareService.getDefaultDesktopSoftware();
        if (software == null) {
            return null;
        }
        return versionService.getCurrentVersion(software.getId(), platform, arch);
    }

    private String buildFeedUrl(String platform, String arch) {
        // 路径里带租户号：electron-updater 自己发请求时**不会**带 tenant-id 头，
        // 只能把租户放进 URL（见 AppSoftwareUpdateController#getLatestYml 的 @TenantIgnore）
        Object tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
        return StrUtil.format("{}/app-api/software/feed/{}/{}/{}/",
                StrUtil.removeSuffix(publicBaseUrl, "/"), tenantId, platform, arch);
    }

    /**
     * 客户端版本是否低于服务端当前发布。
     * 版本号非法、或客户端更高（回滚后的场景）都返回 false（Q90）。
     */
    static boolean isNewer(String clientVersion, String latestVersion) {
        int[] client = parse(clientVersion);
        int[] latest = parse(latestVersion);
        if (client == null || latest == null) {
            return false;
        }
        int length = Math.max(client.length, latest.length);
        for (int i = 0; i < length; i++) {
            int left = i < client.length ? client[i] : 0;
            int right = i < latest.length ? latest[i] : 0;
            if (left != right) {
                return left < right;
            }
        }
        return false;
    }

    private static int[] parse(String version) {
        if (StrUtil.isBlank(version)) {
            return null;
        }
        String[] parts = version.trim().split("\\.");
        if (parts.length < 3 || parts.length > 4) {
            return null;
        }
        int[] result = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                result[i] = Integer.parseInt(parts[i]);
                if (result[i] < 0) {
                    return null;
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return result;
    }

}
