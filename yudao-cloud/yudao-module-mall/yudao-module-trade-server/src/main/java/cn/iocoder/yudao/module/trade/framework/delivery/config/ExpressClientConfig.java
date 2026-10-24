package cn.iocoder.yudao.module.trade.framework.delivery.config;

import cn.iocoder.yudao.module.trade.framework.delivery.core.client.ExpressClient;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.ExpressClientFactory;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.impl.ExpressClientFactoryImpl;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 快递客户端端配置类：
 *
 * 1. 快递客户端工厂 {@link ExpressClientFactory}
 * 2. 默认的快递客户端实现 {@link ExpressClient}
 *
 * @author jason
 */
@Configuration(proxyBeanMethods = false)
public class ExpressClientConfig {

    @Bean
    public ExpressClientFactory expressClientFactory(TradeExpressProperties tradeExpressProperties,
                                                     RestTemplateBuilder restTemplateBuilder) {
        // ⚠️ 快递100 是外部依赖，**不能沿用全局 RestTemplate 的无限超时**：
        //    三方挂起时，前端会一直等到自己的 30s 超时，违反 SC-088「失败 10 秒内降级」。
        //    2s 连接 + 6s 读取 = 最坏 8s，留出 2s 给页面渲染。
        RestTemplate restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(6))
                .build();
        return new ExpressClientFactoryImpl(tradeExpressProperties, restTemplate);
    }

    @Bean
    public ExpressClient defaultExpressClient(ExpressClientFactory expressClientFactory) {
        return expressClientFactory.getDefaultExpressClient();
    }

}
