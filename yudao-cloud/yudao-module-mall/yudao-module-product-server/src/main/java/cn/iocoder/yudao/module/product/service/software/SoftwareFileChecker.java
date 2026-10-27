package cn.iocoder.yudao.module.product.service.software;

/**
 * 回源核对文件是否真的存在（013 / FR-337）
 *
 * 只做 HEAD，**不下载整包** —— 服务端不校验内容哈希，那由客户端下载后完成（FR-338）。
 */
public interface SoftwareFileChecker {

    /**
     * 返回远端文件的 Content-Length；不可访问时抛异常。
     */
    long getContentLength(String fileUrl);

}
