package cn.iocoder.yudao.module.member.controller.app.org.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AppMemberOrgMemberPageReqVO extends PageParam {

    private String keyword;

}
