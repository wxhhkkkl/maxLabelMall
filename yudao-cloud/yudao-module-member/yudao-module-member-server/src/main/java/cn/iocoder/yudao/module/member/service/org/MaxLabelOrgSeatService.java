package cn.iocoder.yudao.module.member.service.org;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgSeatDO;

/**
 * MaxLabel 企业席位 Service
 */
public interface MaxLabelOrgSeatService {

    /** operatorType：1 会员 2 系统用户 */
    void assign(Long orgId, Long memberId, Integer operatorType, Long operatorId);

    void revoke(Long orgId, Long memberId, Integer operatorType, Long operatorId);

    MaxLabelOrgSeatDO getActiveByMemberId(Long memberId);

    java.util.List<MaxLabelOrgSeatDO> getActiveList(Long orgId);

    int getActiveCount(Long orgId);

    /** max(0, 未过期批次席位和 - 有效席位) */
    int getAvailableSeats(Long orgId);

    PageResult<MaxLabelOrgSeatDO> getPage(Long orgId, PageParam pageParam);

}
