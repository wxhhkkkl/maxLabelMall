package cn.iocoder.yudao.module.product.service.software;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareVersionDO;

import java.util.Collection;
import java.util.List;

/**
 * 软件版本 Service（013）
 */
public interface SoftwareVersionService {

    /** 登记（草稿）：校验版本号/唯一性/文件地址/校验和，并回源 HEAD 核对大小 */
    Long registerVersion(SoftwareVersionDO version);

    /** 发布；current=true 时在同一事务内切换当前发布（先清后置） */
    void publishVersion(Long id, boolean current);

    /** 下架：status=2；若它是当前发布，同时清空 current_flag */
    void unpublishVersion(Long id);

    /** 软删除（文件与链接仍可用） */
    void deleteVersion(Long id);

    SoftwareVersionDO getVersion(Long id);

    PageResult<SoftwareVersionDO> getVersionPage(Long softwareId, PageParam pageParam);

    /** 某个 software × platform × arch 的当前发布；没有返回 null */
    SoftwareVersionDO getCurrentVersion(Long softwareId, String platform, String arch);

    /** 一批软件的当前发布版本（服务支持页详情用） */
    List<SoftwareVersionDO> getCurrentVersionList(Collection<Long> softwareIds);

}
