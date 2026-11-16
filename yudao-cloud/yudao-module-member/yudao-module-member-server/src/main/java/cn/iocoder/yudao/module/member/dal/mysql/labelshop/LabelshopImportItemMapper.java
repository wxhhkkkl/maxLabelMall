package cn.iocoder.yudao.module.member.dal.mysql.labelshop;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportItemDO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * MaxLabel LabelShop 导入明细 Mapper
 */
@Mapper
public interface LabelshopImportItemMapper extends BaseMapperX<LabelshopImportItemDO> {

    default PageResult<LabelshopImportItemDO> selectPageByTask(Long taskId, Integer status,
                                                               Integer library, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<LabelshopImportItemDO>()
                .eq(LabelshopImportItemDO::getTaskId, taskId)
                .eqIfPresent(LabelshopImportItemDO::getStatus, status)
                .eqIfPresent(LabelshopImportItemDO::getSourceLibrary, library)
                .orderByAsc(LabelshopImportItemDO::getId));
    }

    /** 按来源库 / 状态分组统计；返回 source_library、status、cnt */
    default List<Map<String, Object>> selectLibraryStats(Long taskId) {
        QueryWrapper<LabelshopImportItemDO> wrapper = new QueryWrapper<>();
        wrapper.select("source_library", "status", "COUNT(*) AS cnt")
                .eq("task_id", taskId)
                .groupBy("source_library", "status");
        return selectMaps(wrapper);
    }

    /** 跨任务查询某目标下已成功的来源模板 */
    default List<LabelshopImportItemDO> selectExistingSuccess(Integer targetScope, Long targetOwnerId,
                                                              Collection<String> sourceRefs) {
        if (sourceRefs == null || sourceRefs.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<LabelshopImportItemDO>()
                .eq(LabelshopImportItemDO::getTargetScope, targetScope)
                .eq(LabelshopImportItemDO::getTargetOwnerId, targetOwnerId)
                .eq(LabelshopImportItemDO::getStatus, 1)
                .in(LabelshopImportItemDO::getSourceRef, sourceRefs));
    }

    default long countByTaskAndFormat(Long taskId, String format) {
        return selectCount(new LambdaQueryWrapperX<LabelshopImportItemDO>()
                .eq(LabelshopImportItemDO::getTaskId, taskId)
                .eq(LabelshopImportItemDO::getFormat, format));
    }

}
