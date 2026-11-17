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
 * ⚠️ 只提供**查询与新增**：记录不可修改、不可删除（Q154）。
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

}
