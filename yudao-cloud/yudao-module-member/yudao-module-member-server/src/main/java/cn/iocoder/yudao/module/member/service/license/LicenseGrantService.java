package cn.iocoder.yudao.module.member.service.license;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGrantPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGrantReqVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGrantRevokeReqVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGrantRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.license.MemberLicenseGrantDO;

/**
 * 授权赠送 Service（020）
 *
 * 一次赠送同时做三件事，**必须在同一事务里**：
 * ① 改变授权（个人：新增一行订阅；企业：改现有有效批次 / 新建赠送批次）
 * ② 写赠送记录（{@code member_license_grant}）
 * ③ 由 {@code @LogRecord} 写操作日志
 *
 * 上限（Q154）：单次 ≤ 12 个月，且赠送后剩余 ≤ 12 个月；额度算法唯一实现在 {@link LicenseGiftQuota}。
 */
public interface LicenseGrantService {

    /**
     * 赠送授权
     *
     * @param reqVO      赠送内容
     * @param operatorId 操作人（后台登录用户）
     */
    LicenseGrantRespVO grant(LicenseGrantReqVO reqVO, Long operatorId);

    /**
     * 赠送记录分页（看板「赠送记录」页签）
     */
    PageResult<MemberLicenseGrantDO> getGrantPage(LicenseGrantPageReqVO reqVO);

    /**
     * 撤回赠送（Q164，2026-11-19）
     *
     * 只允许撤回该对象**最近一笔**、且之后没有新的权益变动；撤回与原赠送在同一事务里，
     * 失败不留痕。记录本身不删，只写 `revoke_*` 三列。
     *
     * @param reqVO      赠送记录编号 + 撤回理由
     * @param operatorId 操作人（后台登录用户）
     */
    Boolean revoke(LicenseGrantRevokeReqVO reqVO, Long operatorId);

}
