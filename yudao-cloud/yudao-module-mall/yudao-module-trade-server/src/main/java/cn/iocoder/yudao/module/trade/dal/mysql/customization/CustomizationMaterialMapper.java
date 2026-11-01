package cn.iocoder.yudao.module.trade.dal.mysql.customization;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationMaterialDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CustomizationMaterialMapper extends BaseMapperX<CustomizationMaterialDO> {

    default PageResult<CustomizationMaterialDO> selectPage(String name, Integer status, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<CustomizationMaterialDO>()
                .likeIfPresent(CustomizationMaterialDO::getName, name)
                .eqIfPresent(CustomizationMaterialDO::getStatus, status)
                .orderByAsc(CustomizationMaterialDO::getSort)
                .orderByDesc(CustomizationMaterialDO::getId));
    }

    default List<CustomizationMaterialDO> selectEnabledList() {
        return selectList(new LambdaQueryWrapperX<CustomizationMaterialDO>()
                .eq(CustomizationMaterialDO::getStatus, 0)
                .orderByAsc(CustomizationMaterialDO::getSort)
                .orderByAsc(CustomizationMaterialDO::getId));
    }

}
