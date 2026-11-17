package cn.iocoder.yudao.module.trade.service.customerservice.bo;

import lombok.Data;

import java.util.List;

/**
 * 客户服务 - 投诉咨询创建 BO（021）
 *
 * C 端自助提交与客服代建共用同一套字段与校验；`source`/`creatorId` 由入口决定。
 */
@Data
public class CustomerServiceTicketCreateReqBO {

    private Long memberId;
    private Integer type;
    private String content;
    private String contactMobile;
    private List<String> images;
    private Integer source;
    private Long creatorId;

}
