package cn.iocoder.yudao.module.member.dal.mysql.share;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelPlatformContentDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MaxLabelPlatformContentMapper extends BaseMapperX<MaxLabelPlatformContentDO> {

    default PageResult<MaxLabelPlatformContentDO> selectAdminPage(String keyword, Integer contentType,
                                                                  String category, Integer status,
                                                                  PageParam pageParam) {
        LambdaQueryWrapperX<MaxLabelPlatformContentDO> wrapper = new LambdaQueryWrapperX<MaxLabelPlatformContentDO>()
                .eqIfPresent(MaxLabelPlatformContentDO::getContentType, contentType)
                .eqIfPresent(MaxLabelPlatformContentDO::getStatus, status)
                .likeIfPresent(MaxLabelPlatformContentDO::getCategory, category);
        appendKeyword(wrapper, keyword);
        return selectPage(pageParam, wrapper.orderByDesc(MaxLabelPlatformContentDO::getId));
    }

    default PageResult<MaxLabelPlatformContentDO> selectOnlinePage(String keyword, Integer contentType,
                                                                    String category, java.math.BigDecimal widthMin,
                                                                    java.math.BigDecimal widthMax,
                                                                    java.math.BigDecimal heightMin,
                                                                    java.math.BigDecimal heightMax,
                                                                    PageParam pageParam) {
        LambdaQueryWrapperX<MaxLabelPlatformContentDO> wrapper = new LambdaQueryWrapperX<MaxLabelPlatformContentDO>()
                .eq(MaxLabelPlatformContentDO::getStatus, 1)
                .eqIfPresent(MaxLabelPlatformContentDO::getContentType, contentType)
                .likeIfPresent(MaxLabelPlatformContentDO::getCategory, category)
                .geIfPresent(MaxLabelPlatformContentDO::getWidthMm, widthMin)
                .leIfPresent(MaxLabelPlatformContentDO::getWidthMm, widthMax)
                .geIfPresent(MaxLabelPlatformContentDO::getHeightMm, heightMin)
                .leIfPresent(MaxLabelPlatformContentDO::getHeightMm, heightMax);
        appendKeyword(wrapper, keyword);
        return selectPage(pageParam, wrapper.orderByDesc(MaxLabelPlatformContentDO::getPublishedTime)
                .orderByDesc(MaxLabelPlatformContentDO::getId));
    }

    private static void appendKeyword(LambdaQueryWrapperX<MaxLabelPlatformContentDO> wrapper, String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return;
        }
        wrapper.and(query -> query.like(MaxLabelPlatformContentDO::getName, keyword)
                .or().like(MaxLabelPlatformContentDO::getCategory, keyword)
                .or().like(MaxLabelPlatformContentDO::getKeywords, keyword));
    }

}
