package cn.iocoder.yudao.module.product.dal.mysql.software;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareCategoryDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SoftwareCategoryMapper extends BaseMapperX<SoftwareCategoryDO> {

    default PageResult<SoftwareCategoryDO> selectPage(String name, Integer status, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<SoftwareCategoryDO>()
                .likeIfPresent(SoftwareCategoryDO::getName, name)
                .eqIfPresent(SoftwareCategoryDO::getStatus, status)
                .orderByAsc(SoftwareCategoryDO::getSort)
                .orderByDesc(SoftwareCategoryDO::getId));
    }

    /** 启用中的分类，按排序升序 */
    default List<SoftwareCategoryDO> selectListByStatus(Integer status) {
        return selectList(new LambdaQueryWrapperX<SoftwareCategoryDO>()
                .eq(SoftwareCategoryDO::getStatus, status)
                .orderByAsc(SoftwareCategoryDO::getSort)
                .orderByAsc(SoftwareCategoryDO::getId));
    }

}
