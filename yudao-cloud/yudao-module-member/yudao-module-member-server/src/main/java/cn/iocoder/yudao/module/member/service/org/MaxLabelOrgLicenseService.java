package cn.iocoder.yudao.module.member.service.org;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgLicenseGrantReqDTO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgLicenseDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MaxLabel 企业授权批次 Service
 */
public interface MaxLabelOrgLicenseService {

    MaxLabelOrgLicenseDO grant(MemberOrgLicenseGrantReqDTO reqDTO);

    PageResult<MaxLabelOrgLicenseDO> getPage(Long orgId, PageParam pageParam);

    /** 未过期批次的席位数之和 */
    int getSeatTotal(Long orgId);

    /** 未过期批次的最大到期日；没有时返回 null */
    LocalDateTime getMaxEndTime(Long orgId);

    List<MaxLabelOrgLicenseDO> getUnexpiredList(Long orgId);

    void markRefundedByOrder(Long orderId);

    void markRefundedByOrderItem(Long orderItemId);

}
