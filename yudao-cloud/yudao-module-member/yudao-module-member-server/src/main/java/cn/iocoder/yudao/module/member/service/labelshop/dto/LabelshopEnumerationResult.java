package cn.iocoder.yudao.module.member.service.labelshop.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 枚举结果：待导入模板 + 来源级失败 + 企业模板用的 STS。
 */
@Data
public class LabelshopEnumerationResult {

    private List<LabelshopSourceItem> items = new ArrayList<>();
    private List<String> errors = new ArrayList<>();
    /** 企业/组织模板下载所需的 STS；未读取或失败时为 null */
    private LabelshopSts sts;

}
