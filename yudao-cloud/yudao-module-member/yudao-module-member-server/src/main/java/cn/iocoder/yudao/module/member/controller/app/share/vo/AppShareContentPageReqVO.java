package cn.iocoder.yudao.module.member.controller.app.share.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "用户 App - 共享内容分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppShareContentPageReqVO extends PageParam {

    @Schema(description = "空间类型：1 个人 2 企业", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer scope;

    @Schema(description = "文件夹编号；企业空间必填")
    private Long folderId;

    @Schema(description = "内容类型：1 模板 2 txt 3 excel 4 csv 5 图片")
    private Integer contentType;

    private String keyword;

}
