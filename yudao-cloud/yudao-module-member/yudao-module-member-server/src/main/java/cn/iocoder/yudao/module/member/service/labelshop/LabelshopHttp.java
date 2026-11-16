package cn.iocoder.yudao.module.member.service.labelshop;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * LabelShop HTTP 传输层。
 *
 * <p>2026-11-13 从 Hutool 换成 JDK {@link HttpClient}：同一个账号、同一份请求体，
 * Hutool 发出的 Exec 请求会被上游以 500 / System.FormatException 拒绝，而 JDK 客户端正常。
 * 同时使用 JDK 默认 TLS 校验（上游证书有效），不再关闭证书链与主机名校验。</p>
 */
@Component
public class LabelshopHttp {

    private final LabelshopImportProperties properties;
    private final HttpClient client;

    public LabelshopHttp(LabelshopImportProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public Result postJson(String url, String body, Map<String, String> headers) {
        return execute(url, body, headers);
    }

    public Result get(String url, Map<String, String> headers) {
        return execute(url, null, headers);
    }

    private Result execute(String url, String body, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        if (headers != null) {
            headers.forEach(builder::header);
        }
        if (body == null) {
            builder.GET();
        } else {
            builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        try {
            HttpResponse<byte[]> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            Map<String, String> cookies = new HashMap<>();
            for (String value : response.headers().allValues("set-cookie")) {
                String pair = value.split(";", 2)[0];
                int idx = pair.indexOf('=');
                if (idx > 0) {
                    cookies.put(pair.substring(0, idx).trim(), pair.substring(idx + 1).trim());
                }
            }
            byte[] bytes = response.body();
            return new Result(response.statusCode(), new String(bytes, StandardCharsets.UTF_8),
                    bytes, cookies);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LabelshopUpstreamException("历史系统请求失败：" + ex.getMessage());
        } catch (Exception ex) {
            throw new LabelshopUpstreamException("历史系统请求失败：" + ex.getMessage());
        }
    }

    public record Result(int status, String body, byte[] bytes, Map<String, String> cookies) {
    }

}
