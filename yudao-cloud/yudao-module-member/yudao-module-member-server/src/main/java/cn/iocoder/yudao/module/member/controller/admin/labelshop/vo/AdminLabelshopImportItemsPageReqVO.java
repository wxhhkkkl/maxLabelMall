package cn.iocoder.yudao.module.member.controller.admin.labelshop.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 导入明细分页请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminLabelshopImportItemsPageReqVO extends PageParam {

    @NotNull(message = "任务编号不能为空")
    private Long taskId;
    /** 1 成功 2 跳过 3 失败；为空则全部 */
    private Integer status;
    /** 12 / 13 / 1；为空则全部 */
    private Integer library;

}
