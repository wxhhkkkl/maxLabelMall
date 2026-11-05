package cn.iocoder.yudao.module.member.service.share;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentPageReqVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentSaveReqVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentUploadReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;

/**
 * MaxLabel 个人 / 企业共享内容 Service
 */
public interface ShareContentService {

    PageResult<MaxLabelShareContentDO> getPage(Long memberId, AppShareContentPageReqVO reqVO);

    /** 本人上传到企业的内容，跨文件夹、不要求文件夹可见（FR-409） */
    PageResult<MaxLabelShareContentDO> getMyEnterprisePage(Long memberId, cn.iocoder.yudao.framework.common.pojo.PageParam pageParam);

    MaxLabelShareContentDO getContent(Long memberId, Long id);

    Long create(Long memberId, AppShareContentSaveReqVO reqVO);

    Long upload(Long memberId, AppShareContentUploadReqVO reqVO) throws Exception;

    void update(Long memberId, AppShareContentSaveReqVO reqVO);

    void delete(Long memberId, Long id);

    boolean canDelete(Long memberId, MaxLabelShareContentDO content);

    /** 文件类内容返回 5 分钟预签名地址；文本类返回 null */
    String getDownloadUrl(MaxLabelShareContentDO content);

}
