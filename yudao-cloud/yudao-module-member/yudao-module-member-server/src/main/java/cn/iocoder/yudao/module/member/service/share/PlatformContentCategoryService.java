package cn.iocoder.yudao.module.member.service.share;

import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelPlatformContentCategoryDO;

import java.util.List;

/**
 * MaxLabel 平台共享内容分类 Service
 */
public interface PlatformContentCategoryService {

    List<MaxLabelPlatformContentCategoryDO> getList();

    Long create(String name, Integer sort);

    void delete(Long id);

}
