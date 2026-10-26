package cn.iocoder.yudao.module.member.dal.mysql.org;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgLicenseDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MaxLabelOrgLicenseMapper extends BaseMapperX<MaxLabelOrgLicenseDO> {

    default MaxLabelOrgLicenseDO selectBySourceOrderItemId(Long sourceOrderItemId) {
        return selectOne(MaxLabelOrgLicenseDO::getSourceOrderItemId, sourceOrderItemId);
    }

    default List<MaxLabelOrgLicenseDO> selectListByOrgId(Long orgId) {
        return selectList(new LambdaQueryWrapperX<MaxLabelOrgLicenseDO>()
                .eq(MaxLabelOrgLicenseDO::getOrgId, orgId)
                .orderByAsc(MaxLabelOrgLicenseDO::getEndTime));
    }

    default List<MaxLabelOrgLicenseDO> selectUnexpiredListByOrgId(Long orgId, LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<MaxLabelOrgLicenseDO>()
                .eq(MaxLabelOrgLicenseDO::getOrgId, orgId)
                .gt(MaxLabelOrgLicenseDO::getEndTime, now)
                .orderByAsc(MaxLabelOrgLicenseDO::getEndTime));
    }

    default PageResult<MaxLabelOrgLicenseDO> selectPageByOrgId(Long orgId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<MaxLabelOrgLicenseDO>()
                .eq(MaxLabelOrgLicenseDO::getOrgId, orgId)
                .orderByDesc(MaxLabelOrgLicenseDO::getEndTime));
    }

    default int updateRefundedByOrderId(Long orderId) {
        return update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MaxLabelOrgLicenseDO>()
                .eq(MaxLabelOrgLicenseDO::getSourceOrderId, orderId)
                .set(MaxLabelOrgLicenseDO::getRefunded, true));
    }

    default int updateRefundedByOrderItemId(Long orderItemId) {
        return update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MaxLabelOrgLicenseDO>()
                .eq(MaxLabelOrgLicenseDO::getSourceOrderItemId, orderItemId)
                .set(MaxLabelOrgLicenseDO::getRefunded, true));
    }

}
