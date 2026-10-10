package cn.iocoder.yudao.module.promotion.controller.app.banner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "用户 App - Banner Response VO")
@Data
public class AppBannerRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "标题不能为空")
    private String title;

    @Schema(description = "跳转链接", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "跳转链接不能为空")
    private String url;

    @Schema(description = "图片地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "图片地址不能为空")
    private String picUrl;

    /**
     * 描述（后台表单里的「描述」字段）。
     *
     * **本项目新增**（2026-10-10）：商城的横幅是**组合式**的 —— 后台配图与文案，
     * 前端负责排版。副标题与胶囊两段文案就来自这个字段（按换行拆，见前端 `utils/banner.ts`）。
     * 上游这个 VO 只有 id/title/url/picUrl 四个字段，不补这个字段的话文案到不了前端。
     */
    @Schema(description = "描述（前端按换行拆成副标题与胶囊）")
    private String memo;

}
