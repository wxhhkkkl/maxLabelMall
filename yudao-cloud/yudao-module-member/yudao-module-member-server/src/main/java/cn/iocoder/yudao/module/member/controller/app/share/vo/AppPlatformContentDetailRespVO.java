package cn.iocoder.yudao.module.member.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "用户 App - 平台共享内容详情 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppPlatformContentDetailRespVO extends AppPlatformContentRespVO {

    private Integer storageType;
    private String content;
    private String downloadUrl;
    private String mimeType;

}
