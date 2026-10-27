package cn.iocoder.yudao.module.product.service.software;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareDO;

import java.util.Collection;
import java.util.List;

/**
 * 软件 / 驱动 Service（013）
 */
public interface SoftwareService {

    Long createSoftware(SoftwareDO software);

    void updateSoftware(SoftwareDO software);

    void deleteSoftware(Long id);

    SoftwareDO getSoftware(Long id);

    PageResult<SoftwareDO> getSoftwarePage(Long categoryId, Integer type, Integer status,
                                           String title, PageParam pageParam);

    /** 服务支持页：给定分类下、启用且标记可展示的软件 */
    List<SoftwareDO> getSupportSoftwareList(Collection<Long> categoryIds);

    /** 客户端更新：按 id 取启用中的桌面端软件；不存在或不是桌面端软件返回 null */
    SoftwareDO getEnabledDesktopSoftware(Long softwareId);

    /**
     * 客户端更新：未指定 softwareId 时的默认桌面端软件 ——
     * 取「启用、type=1、且存在当前发布版本」中 id 最小的一款（契约的确定性规则）。
     */
    SoftwareDO getDefaultDesktopSoftware();

}
