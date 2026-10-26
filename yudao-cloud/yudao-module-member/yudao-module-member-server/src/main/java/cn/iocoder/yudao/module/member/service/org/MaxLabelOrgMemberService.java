package cn.iocoder.yudao.module.member.service.org;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgMemberDO;

/**
 * MaxLabel 企业成员 Service
 */
public interface MaxLabelOrgMemberService {

    /** operatorType：1 会员 2 系统用户（运营代操作） */
    void addMember(Long orgId, String mobile, Integer operatorType, Long operatorId);

    void removeMember(Long orgId, Long memberId, Integer operatorType, Long operatorId);

    void updateRole(Long orgId, Long memberId, Integer role, Integer operatorType, Long operatorId);

    void transferOwner(Long orgId, Long targetMemberId, Integer operatorType, Long operatorId);

    void leave(Long memberId);

    PageResult<MaxLabelOrgMemberDO> getMemberPage(Long orgId, String keyword, PageParam pageParam);

    MaxLabelOrgMemberDO getByMemberId(Long memberId);

    int getMemberCount(Long orgId);

    /** 校验操作者：会员需为企业 OWNER/ADMIN；系统用户由 @PreAuthorize 把关 */
    void validateOperator(Long orgId, Integer operatorType, Long operatorId, boolean ownerOnly);

}
