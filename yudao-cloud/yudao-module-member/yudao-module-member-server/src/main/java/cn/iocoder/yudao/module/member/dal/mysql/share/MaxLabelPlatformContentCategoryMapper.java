package cn.iocoder.yudao.module.member.dal.mysql.share;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelPlatformContentCategoryDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MaxLabelPlatformContentCategoryMapper extends BaseMapperX<MaxLabelPlatformContentCategoryDO> {

    default List<MaxLabelPlatformContentCategoryDO> selectListOrdered() {
        return selectList(new LambdaQueryWrapperX<MaxLabelPlatformContentCategoryDO>()
                .orderByAsc(MaxLabelPlatformContentCategoryDO::getSort)
                .orderByAsc(MaxLabelPlatformContentCategoryDO::getId));
    }

    default MaxLabelPlatformContentCategoryDO selectByName(String name) {
        return selectOne(new LambdaQueryWrapperX<MaxLabelPlatformContentCategoryDO>()
                .eq(MaxLabelPlatformContentCategoryDO::getName, name));
    }

}
