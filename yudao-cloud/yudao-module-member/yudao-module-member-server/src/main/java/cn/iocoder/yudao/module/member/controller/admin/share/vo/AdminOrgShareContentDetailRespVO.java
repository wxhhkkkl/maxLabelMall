package cn.iocoder.yudao.module.member.controller.admin.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 企业共享内容详情 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminOrgShareContentDetailRespVO extends AdminOrgShareContentRespVO {

    private Integer storageType;
    private String content;
    private String downloadUrl;
    private String mimeType;

}
