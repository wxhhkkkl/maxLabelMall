package cn.iocoder.yudao.module.member.service.share;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MaxLabel 共享内容空间的配置项。
 */
@Component
@ConfigurationProperties(prefix = "yudao.maxlabel.share")
@Data
public class ShareProperties {

    /** 单文件上限，默认 16 MB */
    private long contentMaxBytes = ShareConstants.DEFAULT_MAX_CONTENT_BYTES;

}
