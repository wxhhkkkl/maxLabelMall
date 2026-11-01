package cn.iocoder.yudao.module.trade.dal.mysql.customization;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationAreaRuleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CustomizationAreaRuleMapper extends BaseMapperX<CustomizationAreaRuleDO> {

    default List<CustomizationAreaRuleDO> selectListByMaterialId(Long materialId) {
        return selectList(new LambdaQueryWrapperX<CustomizationAreaRuleDO>()
                .eq(CustomizationAreaRuleDO::getMaterialId, materialId)
                .orderByAsc(CustomizationAreaRuleDO::getMinArea));
    }

    default List<CustomizationAreaRuleDO> selectListByMaterialIds(Collection<Long> materialIds) {
        return selectList(new LambdaQueryWrapperX<CustomizationAreaRuleDO>()
                .in(CustomizationAreaRuleDO::getMaterialId, materialIds)
                .orderByAsc(CustomizationAreaRuleDO::getMaterialId)
                .orderByAsc(CustomizationAreaRuleDO::getMinArea));
    }

    default int deleteByMaterialId(Long materialId) {
        return delete(new LambdaQueryWrapperX<CustomizationAreaRuleDO>()
                .eq(CustomizationAreaRuleDO::getMaterialId, materialId));
    }

}
