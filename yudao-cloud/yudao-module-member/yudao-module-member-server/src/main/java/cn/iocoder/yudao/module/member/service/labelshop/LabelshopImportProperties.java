package cn.iocoder.yudao.module.member.service.labelshop;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LabelShop 模板导入配置项。
 */
@Component
@ConfigurationProperties(prefix = "yudao.maxlabel.labelshop")
@Data
public class LabelshopImportProperties {

    /** Legacy API 基地址 */
    private String baseUrl = "https://o.codingv.com/v1";
    /** 机器码；为空时按主机名派生 */
    private String mid;
    /** 客户端短版本 */
    private String uaShort = "6.39";
    /** 客户端完整版本 */
    private String uaLong = "6.39.2511.36";
    /** 客户端 build */
    private String uaBuild = "2511";
    /** 客户端 OS 片段 */
    private String uaOs = "Windows 10; x64";
    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 30_000;
    /** 读取超时（毫秒） */
    private int readTimeoutMs = 60_000;
    /** 每批刷新计数/用量的条数 */
    private int batchSize = 20;

}
