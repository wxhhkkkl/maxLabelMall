package cn.iocoder.yudao.module.product.service.software;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

/**
 * 软件包存储 Service 实现类（013）
 */
@Service
@Validated
public class SoftwareStorageServiceImpl implements SoftwareStorageService {

    /** 已配置的存储域名（线上为 img.yuwangchenfa.com），可按环境覆盖 */
    @Value("${maxlabel.software.storage-domain:img.yuwangchenfa.com}")
    private String storageDomain;

    @Override
    public String buildDirectory(String platform, String arch, String version) {
        return StrUtil.format("software/{}-{}/{}", platform, arch, version);
    }

    @Override
    public boolean isAllowedUrl(String fileUrl) {
        if (StrUtil.isBlank(fileUrl) || StrUtil.isBlank(storageDomain)) {
            return false;
        }
        try {
            URI uri = URI.create(fileUrl);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && (uri.getHost().equals(storageDomain) || uri.getHost().endsWith("." + storageDomain));
        } catch (Exception e) {
            return false;
        }
    }

}
