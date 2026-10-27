package cn.iocoder.yudao.module.product.service.software;

import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URI;

/**
 * 基于 HTTP HEAD 的回源核对实现（013）
 *
 * 超时设短一些：登记是运营的交互动作，不能因为对象存储慢而长时间挂住请求。
 */
@Component
public class HttpSoftwareFileChecker implements SoftwareFileChecker {

    private static final int TIMEOUT_MS = 5_000;

    @Override
    public long getContentLength(String fileUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(fileUrl).toURL().openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HEAD 返回 " + code);
            }
            return connection.getContentLengthLong();
        } catch (Exception e) {
            throw new IllegalStateException("无法访问文件：" + fileUrl, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

}
