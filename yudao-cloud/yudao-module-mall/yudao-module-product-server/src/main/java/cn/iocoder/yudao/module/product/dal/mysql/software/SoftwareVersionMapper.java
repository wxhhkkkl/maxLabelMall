package cn.iocoder.yudao.module.product.dal.mysql.software;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareVersionDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SoftwareVersionMapper extends BaseMapperX<SoftwareVersionDO> {

    default PageResult<SoftwareVersionDO> selectPageBySoftwareId(Long softwareId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<SoftwareVersionDO>()
                .eq(SoftwareVersionDO::getSoftwareId, softwareId)
                .orderByDesc(SoftwareVersionDO::getId));
    }

    default List<SoftwareVersionDO> selectListBySoftwareId(Long softwareId) {
        return selectList(new LambdaQueryWrapperX<SoftwareVersionDO>()
                .eq(SoftwareVersionDO::getSoftwareId, softwareId)
                .orderByDesc(SoftwareVersionDO::getId));
    }

    /** 当前发布（每 software × platform × arch 至多一条） */
    default SoftwareVersionDO selectCurrent(Long softwareId, String platform, String arch) {
        return selectOne(new LambdaQueryWrapperX<SoftwareVersionDO>()
                .eq(SoftwareVersionDO::getSoftwareId, softwareId)
                .eq(SoftwareVersionDO::getPlatform, platform)
                .eq(SoftwareVersionDO::getArch, arch)
                .eq(SoftwareVersionDO::getCurrentFlag, 1)
                .last("LIMIT 1"));
    }

    /** 该软件全部当前发布版本（用于服务支持页详情，每平台/架构一条） */
    default List<SoftwareVersionDO> selectListCurrent(Collection<Long> softwareIds) {
        return selectList(new LambdaQueryWrapperX<SoftwareVersionDO>()
                .in(SoftwareVersionDO::getSoftwareId, softwareIds)
                .eq(SoftwareVersionDO::getCurrentFlag, 1)
                .eq(SoftwareVersionDO::getStatus, 0)
                .orderByAsc(SoftwareVersionDO::getPlatform)
                .orderByAsc(SoftwareVersionDO::getArch));
    }

    default SoftwareVersionDO selectBySkuKey(Long softwareId, String platform, String arch, String version) {
        return selectOne(new LambdaQueryWrapperX<SoftwareVersionDO>()
                .eq(SoftwareVersionDO::getSoftwareId, softwareId)
                .eq(SoftwareVersionDO::getPlatform, platform)
                .eq(SoftwareVersionDO::getArch, arch)
                .eq(SoftwareVersionDO::getVersion, version));
    }

    /**
     * 下架：状态置 2，并显式把 current_flag 置 NULL。
     * ⚠️ 不能用 updateById —— MyBatis-Plus 默认忽略 null 字段，current_flag 会清不掉。
     */
    default void unpublishById(Long id, Integer status) {
        update(new LambdaUpdateWrapper<SoftwareVersionDO>()
                .eq(SoftwareVersionDO::getId, id)
                .set(SoftwareVersionDO::getStatus, status)
                .set(SoftwareVersionDO::getCurrentFlag, null));
    }

    /**
     * 清掉该组合的当前发布标记（置 NULL）。与随后置 1 的操作放在同一事务里，
     * 由唯一索引 uk_current 兜住并发。
     */
    default void clearCurrentFlag(Long softwareId, String platform, String arch) {
        update(new LambdaUpdateWrapper<SoftwareVersionDO>()
                .eq(SoftwareVersionDO::getSoftwareId, softwareId)
                .eq(SoftwareVersionDO::getPlatform, platform)
                .eq(SoftwareVersionDO::getArch, arch)
                .eq(SoftwareVersionDO::getCurrentFlag, 1)
                .set(SoftwareVersionDO::getCurrentFlag, null));
    }

}
