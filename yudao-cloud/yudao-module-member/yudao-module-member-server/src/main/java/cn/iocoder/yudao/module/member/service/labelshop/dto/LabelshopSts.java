package cn.iocoder.yudao.module.member.service.labelshop.dto;

import lombok.Data;

/**
 * 阿里云 STS 临时凭据。
 */
@Data
public class LabelshopSts {

    private String accessKeyId;
    private String accessKeySecret;
    private String securityToken;
    private String bucketEndpoint;
    private String bucketTmpl;
    private String bucketImage;

}
