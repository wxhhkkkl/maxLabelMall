package cn.iocoder.yudao.module.member.service.share;

import cn.iocoder.yudao.module.member.controller.app.share.vo.ShareSpaceOverviewRespVO;
import cn.iocoder.yudao.module.member.service.share.dto.ShareEnterpriseAccess;

/**
 * MaxLabel 共享空间 Service
 */
public interface ShareSpaceService {

    ShareSpaceOverviewRespVO getOverview(Long memberId);

    /** 会员的企业空间上下文；未加入企业时返回 null */
    ShareEnterpriseAccess getEnterpriseAccess(Long memberId);

    long getPersonalQuotaBytes(Long memberId);

    long getPersonalUsedBytes(Long memberId);

    long getEnterpriseUsedBytes(Long orgId);

    /** 锁住空间行；不存在时惰性创建 */
    void lockSpace(Integer scope, Long ownerId);

    /** 锁内重算并回写 used_bytes */
    void refreshUsedBytes(Integer scope, Long ownerId);

}
