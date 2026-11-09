package cn.iocoder.yudao.module.member.service.share;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentSaveReqVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentStatusReqVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentUploadReqVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppPlatformContentPageReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelPlatformContentDO;

/**
 * MaxLabel 平台共享内容 Service
 */
public interface PlatformContentService {

    PageResult<MaxLabelPlatformContentDO> getOnlinePage(AppPlatformContentPageReqVO reqVO);

    MaxLabelPlatformContentDO getOnline(Long id);

    PageResult<MaxLabelPlatformContentDO> getAdminPage(AdminPlatformContentPageReqVO reqVO);

    MaxLabelPlatformContentDO getAdmin(Long id);

    Long upload(Long userId, AdminPlatformContentUploadReqVO reqVO) throws Exception;

    Long create(Long userId, AdminPlatformContentSaveReqVO reqVO);

    void update(Long userId, AdminPlatformContentSaveReqVO reqVO);

    void updateStatus(AdminPlatformContentStatusReqVO reqVO);

    void delete(Long id);

    String getDownloadUrl(MaxLabelPlatformContentDO content);

}
