package cn.iocoder.yudao.module.trade.service.customerservice.bo;

import lombok.Data;

import java.util.List;

/**
 * 客户服务 - 维修申请创建 BO（021 / US71）
 *
 * 一份申请只对应一台设备；C 端自助与客服代建共用。
 */
@Data
public class CustomerServiceRepairCreateReqBO {

    private Long memberId;
    private String deviceModel;
    private String faultDesc;
    private String contactMobile;
    private String receiverName;
    private String receiverMobile;
    private String receiverArea;
    private String receiverDetail;
    private List<String> images;
    private Integer source;
    private Long creatorId;

}
