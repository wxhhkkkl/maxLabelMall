package cn.iocoder.yudao.module.product.dal.mysql.software;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareVersionDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 该软件「最新的一条当前发布」（2026-11-18）
     *
     * 商品详情页只有一个下载按钮：多平台/多架构时取 release_time 最新的一条（NULL 排最后），
     * 再按 id 兜底，保证结果稳定。
     */
    default SoftwareVersionDO selectLatestCurrent(Long softwareId) {
        return selectOne(new LambdaQueryWrapperX<SoftwareVersionDO>()
                .eq(SoftwareVersionDO::getSoftwareId, softwareId)
                .eq(SoftwareVersionDO::getCurrentFlag, 1)
                .eq(SoftwareVersionDO::getStatus, 0)
                .orderByDesc(SoftwareVersionDO::getReleaseTime)
                .orderByDesc(SoftwareVersionDO::getId)
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

    /**
     * 按「软件 × 平台 × 架构 × 版本号」查记录，**包含已软删除**。
     *
     * ⚠️ 不能用「逻辑删除的普通查询」代替：唯一索引 `uk_version` 不含 `deleted`，
     * 软删除的墓碑行仍占着键位，而逻辑删除查询看不见它 —— 直接 insert 会撞唯一索引，
     * 表现就是「删了再传同版本号」报 500。
     */
    @Select("SELECT * FROM maxlabel_software_version"
            + " WHERE software_id = #{softwareId} AND platform = #{platform}"
            + " AND arch = #{arch} AND version = #{version}"
            + " LIMIT 1")
    SoftwareVersionDO selectBySkuKeyIncludingDeleted(@Param("softwareId") Long softwareId,
                                                     @Param("platform") String platform,
                                                     @Param("arch") String arch,
                                                     @Param("version") String version);

    /**
     * 复活一条已软删除的版本记录：覆盖为新上传的文件，状态回到传入值、清掉当前发布标记。
     *
     * ⚠️ 不能用 updateById：逻辑删除会给 UPDATE 自动带上 `deleted = 0` 条件，改不到墓碑行。
     */
    @Update("UPDATE maxlabel_software_version"
            + " SET deleted = FALSE, status = #{status}, current_flag = NULL,"
            + " file_name = #{fileName}, file_size = #{fileSize}, sha512 = #{sha512},"
            + " file_url = #{fileUrl}, object_key = #{objectKey},"
            + " release_notes = #{releaseNotes}, release_time = #{releaseTime},"
            + " update_time = NOW()"
            + " WHERE id = #{id} AND deleted = TRUE")
    int reviveDeletedVersion(SoftwareVersionDO version);

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
     * 清掉该组合的当前发布标记（置 NULL），**包含已软删除的墓碑行**。
     *
     * ⚠️ 不能用 update + wrapper：逻辑删除会给 UPDATE 自动带上 `deleted = 0`，
     * 清不掉删除时留下的 `current_flag = 1`。而唯一索引 uk_current 不看 deleted，
     * 残留的 1 会让同组合之后的「设为当前发布」永远撞唯一索引。
     *
     * 与随后置 1 的操作放在同一事务里，由 uk_current 兜住并发。
     */
    @Update("UPDATE maxlabel_software_version"
            + " SET current_flag = NULL, update_time = NOW()"
            + " WHERE software_id = #{softwareId} AND platform = #{platform}"
            + " AND arch = #{arch} AND current_flag = 1")
    int clearCurrentFlag(@Param("softwareId") Long softwareId,
                         @Param("platform") String platform,
                         @Param("arch") String arch);

    /**
     * 软删除一条版本，并把 current_flag 置 NULL。
     *
     * 墓碑上留着 `current_flag = 1` 会一直占着 uk_current（该索引不含 deleted），
     * 挡住同组合的下一个当前发布。文件与已发出的链接不受影响（Q87 / FR-353）。
     */
    @Update("UPDATE maxlabel_software_version"
            + " SET deleted = TRUE, current_flag = NULL, update_time = NOW()"
            + " WHERE id = #{id} AND deleted = FALSE")
    int deleteByIdClearingCurrent(@Param("id") Long id);

}
