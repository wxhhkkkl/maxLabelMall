package cn.iocoder.yudao.module.member.dal.mysql.template;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelSharedTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Mapper
public interface MaxLabelSharedTemplateMapper extends BaseMapperX<MaxLabelSharedTemplateDO> {

    default MaxLabelSharedTemplateDO selectBySourceTemplateId(Long sourceTemplateId) {
        return selectOne(MaxLabelSharedTemplateDO::getSourceTemplateId, sourceTemplateId);
    }

    default List<MaxLabelSharedTemplateDO> selectListBySourceTemplateIds(Collection<Long> sourceTemplateIds) {
        return selectList(MaxLabelSharedTemplateDO::getSourceTemplateId, sourceTemplateIds);
    }

    default PageResult<MaxLabelSharedTemplateDO> selectPublicPage(PageParam pageParam, String keyword,
                                                                 String category, BigDecimal widthMin,
                                                                 BigDecimal widthMax, BigDecimal heightMin,
                                                                 BigDecimal heightMax) {
        return selectPage(pageParam, new LambdaQueryWrapperX<MaxLabelSharedTemplateDO>()
                .eq(MaxLabelSharedTemplateDO::getAuditStatus, MaxLabelSharedTemplateDO.AUDIT_APPROVED)
                .likeIfPresent(MaxLabelSharedTemplateDO::getName, keyword)
                .likeIfPresent(MaxLabelSharedTemplateDO::getKeywords, keyword)
                .eqIfPresent(MaxLabelSharedTemplateDO::getCategory, category)
                .geIfPresent(MaxLabelSharedTemplateDO::getWidthMm, widthMin)
                .leIfPresent(MaxLabelSharedTemplateDO::getWidthMm, widthMax)
                .geIfPresent(MaxLabelSharedTemplateDO::getHeightMm, heightMin)
                .leIfPresent(MaxLabelSharedTemplateDO::getHeightMm, heightMax)
                .orderByDesc(MaxLabelSharedTemplateDO::getPublishedTime));
    }

    default PageResult<MaxLabelSharedTemplateDO> selectAdminPage(PageParam pageParam, Integer auditStatus, String keyword) {
        return selectPage(pageParam, new LambdaQueryWrapperX<MaxLabelSharedTemplateDO>()
                .eqIfPresent(MaxLabelSharedTemplateDO::getAuditStatus, auditStatus)
                .likeIfPresent(MaxLabelSharedTemplateDO::getName, keyword)
                .orderByDesc(MaxLabelSharedTemplateDO::getCreateTime));
    }

}
