package cn.iocoder.yudao.module.member.dal.mysql.template;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelLabelTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MaxLabelLabelTemplateMapper extends BaseMapperX<MaxLabelLabelTemplateDO> {

    default MaxLabelLabelTemplateDO selectByIdAndMemberId(Long id, Long memberId) {
        return selectOne(new LambdaQueryWrapperX<MaxLabelLabelTemplateDO>()
                .eq(MaxLabelLabelTemplateDO::getId, id)
                .eq(MaxLabelLabelTemplateDO::getMemberId, memberId));
    }

    default PageResult<MaxLabelLabelTemplateDO> selectPageByMemberId(Long memberId, String keyword, PageParam pageParam) {
        LambdaQueryWrapperX<MaxLabelLabelTemplateDO> wrapper = new LambdaQueryWrapperX<MaxLabelLabelTemplateDO>()
                .eq(MaxLabelLabelTemplateDO::getMemberId, memberId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(inner -> inner.like(MaxLabelLabelTemplateDO::getName, keyword)
                    .or().like(MaxLabelLabelTemplateDO::getKeywords, keyword));
        }
        return selectPage(pageParam, wrapper.orderByDesc(MaxLabelLabelTemplateDO::getCreateTime));
    }

    default List<MaxLabelLabelTemplateDO> selectListByMemberId(Long memberId) {
        return selectList(new LambdaQueryWrapperX<MaxLabelLabelTemplateDO>()
                .eq(MaxLabelLabelTemplateDO::getMemberId, memberId));
    }

    default PageResult<MaxLabelLabelTemplateDO> selectPageByMemberId(Long memberId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<MaxLabelLabelTemplateDO>()
                .eq(MaxLabelLabelTemplateDO::getMemberId, memberId)
                .orderByDesc(MaxLabelLabelTemplateDO::getCreateTime));
    }

}
