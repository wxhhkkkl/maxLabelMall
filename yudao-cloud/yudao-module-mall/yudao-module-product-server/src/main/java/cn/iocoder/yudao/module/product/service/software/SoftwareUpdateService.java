package cn.iocoder.yudao.module.product.service.software;

import cn.iocoder.yudao.module.product.controller.app.software.vo.AppSoftwareUpdateCheckRespVO;

/**
 * 客户端更新判定 Service（013 / Q89）
 *
 * 「有没有更新」由**服务端**决定；客户端只上报当前版本与平台架构，自己不比较版本号。
 */
public interface SoftwareUpdateService {

    /**
     * 检查更新。任何无法判定或不需要更新的情况都返回 {@code hasUpdate=false}，不抛异常。
     */
    AppSoftwareUpdateCheckRespVO checkUpdate(Long softwareId, String currentVersion,
                                             String platform, String arch);

    /**
     * 渲染 electron-updater 的 latest.yml。
     *
     * @return 无当前发布时返回 null（由控制器转 404）
     */
    String renderLatestYml(String platform, String arch);

}
