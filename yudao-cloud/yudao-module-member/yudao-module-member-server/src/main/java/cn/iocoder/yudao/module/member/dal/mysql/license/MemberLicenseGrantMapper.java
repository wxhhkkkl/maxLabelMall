package cn.iocoder.yudao.module.member.dal.mysql.license;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGrantPageReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.license.MemberLicenseGrantDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 授权赠送记录 Mapper
 *
 * ⚠️ 记录**不可删除**；撤回只写 `revoke_*` 三列（2026-11-19 起允许撤回，见 Q164）。
 */
@Mapper
public interface MemberLicenseGrantMapper extends BaseMapperX<MemberLicenseGrantDO> {

    default PageResult<MemberLicenseGrantDO> selectPage(LicenseGrantPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MemberLicenseGrantDO>()
                .eqIfPresent(MemberLicenseGrantDO::getTargetType, reqVO.getTargetType())
                .eqIfPresent(MemberLicenseGrantDO::getTargetId, reqVO.getTargetId())
                .eqIfPresent(MemberLicenseGrantDO::getOperatorId, reqVO.getOperatorId())
                .betweenIfPresent(MemberLicenseGrantDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(MemberLicenseGrantDO::getId));
    }

    /**
     * 某对象（会员 / 企业）**最近一笔**赠送。
     *
     * 撤回只允许撤最近一笔（Q164）：更早的赠送已经被后续权益覆盖，回退会算错。
     */
    default MemberLicenseGrantDO selectLatestByTarget(Integer targetType, Long targetId) {
        return selectOne(new LambdaQueryWrapperX<MemberLicenseGrantDO>()
                .eq(MemberLicenseGrantDO::getTargetType, targetType)
                .eq(MemberLicenseGrantDO::getTargetId, targetId)
                .orderByDesc(MemberLicenseGrantDO::getId)
                .last("LIMIT 1"));
    }

}
