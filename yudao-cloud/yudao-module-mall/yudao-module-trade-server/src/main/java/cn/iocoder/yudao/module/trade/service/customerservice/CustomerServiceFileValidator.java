package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.hutool.core.util.StrUtil;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMER_SERVICE_FILE_INVALID;

/**
 * 客户服务图片附件校验（021 / FR-580）
 *
 * 只接收图片 URL：数量 ≤ 5、扩展名 ∈ {jpg,jpeg,png,webp}、域名必须属于既有文件域名。
 * ⚠️ 单张 10MB 由上传接口自身兜底（URL 里没有大小信息），这里不再假装校验。
 */
@Component
public class CustomerServiceFileValidator {

    public static final int MAX_COUNT = 5;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    @Value("${yudao.customer-service.file-domain:https://img.yuwangchenfa.com}")
    private String fileDomain;

    public void validate(List<String> images) {
        if (images == null) {
            return;
        }
        if (images.size() > MAX_COUNT) {
            throw exception(CUSTOMER_SERVICE_FILE_INVALID);
        }
        for (String url : images) {
            if (StrUtil.isBlank(url)) {
                throw exception(CUSTOMER_SERVICE_FILE_INVALID);
            }
            if (StrUtil.isNotBlank(fileDomain) && !url.startsWith(fileDomain)) {
                throw exception(CUSTOMER_SERVICE_FILE_INVALID);
            }
            if (!ALLOWED_EXTENSIONS.contains(extensionOf(url))) {
                throw exception(CUSTOMER_SERVICE_FILE_INVALID);
            }
        }
    }

    /** 优先取 URL 路径部分，去掉 query 后取扩展名 */
    static String extensionOf(@NotNull String url) {
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            path = path.substring(slash + 1);
        }
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase();
    }

}
