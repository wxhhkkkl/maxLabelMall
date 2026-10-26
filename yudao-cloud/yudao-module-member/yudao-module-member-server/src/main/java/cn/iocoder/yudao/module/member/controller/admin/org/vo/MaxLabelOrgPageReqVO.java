package cn.iocoder.yudao.module.member.controller.admin.org.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MaxLabelOrgPageReqVO extends PageParam {

    private String name;
    private Integer status;

}
