package cn.iocoder.yudao.module.product.dal.mysql.software;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SoftwareMapper extends BaseMapperX<SoftwareDO> {

    default PageResult<SoftwareDO> selectPage(Long categoryId, Integer type, Integer status,
                                              String title, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<SoftwareDO>()
                .eqIfPresent(SoftwareDO::getCategoryId, categoryId)
                .eqIfPresent(SoftwareDO::getType, type)
                .eqIfPresent(SoftwareDO::getStatus, status)
                .likeIfPresent(SoftwareDO::getTitle, title)
                .orderByDesc(SoftwareDO::getId));
    }

    default long selectCountByCategoryId(Long categoryId) {
        return selectCount(SoftwareDO::getCategoryId, categoryId);
    }

    /** 服务支持页：启用的分类下、启用的、标记为可展示的软件 */
    default List<SoftwareDO> selectListForSupport(Collection<Long> categoryIds) {
        return selectList(new LambdaQueryWrapperX<SoftwareDO>()
                .in(SoftwareDO::getCategoryId, categoryIds)
                .eq(SoftwareDO::getStatus, 0)
                .eq(SoftwareDO::getShowInSupport, true)
                .orderByDesc(SoftwareDO::getId));
    }

    /** 客户端更新：按类型取启用中的软件 */
    default List<SoftwareDO> selectListByTypeAndStatus(Integer type, Integer status) {
        return selectList(new LambdaQueryWrapperX<SoftwareDO>()
                .eq(SoftwareDO::getType, type)
                .eq(SoftwareDO::getStatus, status)
                .orderByAsc(SoftwareDO::getId));
    }

}
