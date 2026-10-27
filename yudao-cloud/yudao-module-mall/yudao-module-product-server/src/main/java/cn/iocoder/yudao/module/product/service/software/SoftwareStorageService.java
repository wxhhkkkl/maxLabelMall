package cn.iocoder.yudao.module.product.service.software;

/**
 * 软件包存储 Service（013）
 *
 * 只负责「对象目录规则」与「下载地址白名单校验」。
 * 预签名直传**复用 infra 既有端点** `GET /admin-api/infra/file/presigned-url`（R77），
 * 本服务不再自己签名，避免在 product 模块重复实现存储能力（宪法原则 III）。
 */
public interface SoftwareStorageService {

    /**
     * 对象目录：software/{platform}-{arch}/{version}
     * 传给 infra 的 presigned-url 接口时作为 `directory` 参数。
     */
    String buildDirectory(String platform, String arch, String version);

    /** 文件地址是否属于已配置的存储域名且为 https（FR-335 / Q85） */
    boolean isAllowedUrl(String fileUrl);

}
