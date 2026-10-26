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

    /**
     * 未过期、**未退款且未折算**的批次。
     *
     * 退款批次与折算批次的剩余时长都已经被别的批次取代，只保留在列表里做留痕，
     * 不再参与权益解析、席位容量、企业到期日与下次续费起点。
     */
    default List<MaxLabelOrgLicenseDO> selectUnexpiredNonRefundedListByOrgId(Long orgId, LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<MaxLabelOrgLicenseDO>()
                .eq(MaxLabelOrgLicenseDO::getOrgId, orgId)
                .gt(MaxLabelOrgLicenseDO::getEndTime, now)
                .eq(MaxLabelOrgLicenseDO::getRefunded, false)
                .isNull(MaxLabelOrgLicenseDO::getFoldedTime)
                .orderByAsc(MaxLabelOrgLicenseDO::getEndTime));
    }

    /**
     * 把企业当前仍在生效的批次全部标记为「已折算」。
     *
     * 只动「未过期 + 未退款 + 未折算」的行：前两种本来就不参与计算，
     * 重复折算会把折算时间刷新一遍，没有意义。
     */
    default int updateFoldedByOrgId(Long orgId, Long excludeId, LocalDateTime now) {
        return update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MaxLabelOrgLicenseDO>()
                .eq(MaxLabelOrgLicenseDO::getOrgId, orgId)
                .ne(MaxLabelOrgLicenseDO::getId, excludeId)
                .gt(MaxLabelOrgLicenseDO::getEndTime, now)
                .eq(MaxLabelOrgLicenseDO::getRefunded, false)
                .isNull(MaxLabelOrgLicenseDO::getFoldedTime)
                .set(MaxLabelOrgLicenseDO::getFoldedTime, now));
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
