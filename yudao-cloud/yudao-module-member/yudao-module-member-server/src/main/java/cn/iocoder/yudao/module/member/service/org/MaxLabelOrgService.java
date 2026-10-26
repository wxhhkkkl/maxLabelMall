package cn.iocoder.yudao.module.member.service.org;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;

/**
 * MaxLabel 企业 Service
 */
public interface MaxLabelOrgService {

    Long createOrg(String name, Long ownerMemberId, String remark);

    void updateOrg(Long id, String name, String remark);

    void updateStatus(Long id, Integer status);

    MaxLabelOrgDO getOrg(Long id);

    MaxLabelOrgDO validateOrgEnabled(Long id);

    PageResult<MaxLabelOrgDO> getOrgPage(String name, Integer status, PageParam pageParam);

    /** 会员所属企业；未加入任何企业时返回 null */
    MemberOrgRespDTO getMemberOrg(Long memberId);

    /** 要求当前会员是启用企业的 OWNER/ADMIN，否则抛业务错 */
    MemberOrgRespDTO requireOrgAdmin(Long memberId);

    /** 锁企业行并校验启用状态 */
    MaxLabelOrgDO lockOrg(Long id);

}
