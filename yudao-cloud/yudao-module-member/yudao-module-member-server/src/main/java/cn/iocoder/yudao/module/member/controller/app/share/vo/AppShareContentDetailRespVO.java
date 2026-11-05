package cn.iocoder.yudao.module.member.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "用户 App - 共享内容详情 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppShareContentDetailRespVO extends AppShareContentRespVO {

    @Schema(description = "存储方式：1 文本正文 2 文件地址")
    private Integer storageType;

    @Schema(description = "文本正文（模板 / txt / csv）")
    private String content;

    @Schema(description = "文件下载地址（5 分钟预签名；excel / 图片）")
    private String downloadUrl;

    private String mimeType;

}
