package cn.iocoder.yudao.module.member.service.template;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.template.vo.AdminSharedTemplatePageReqVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppSharedTemplatePageReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelLabelTemplateDO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelSharedTemplateDO;

public interface SharedTemplateService {

    MaxLabelSharedTemplateDO submit(Long memberId, MaxLabelLabelTemplateDO template,
                                    String category, String keywords);

    void cancel(Long memberId, Long sourceTemplateId);

    void deleteBySource(Long sourceTemplateId);

    PageResult<MaxLabelSharedTemplateDO> getPublicPage(AppSharedTemplatePageReqVO reqVO);

    MaxLabelSharedTemplateDO getPublicContent(Long id);

    PageResult<MaxLabelSharedTemplateDO> getAdminPage(AdminSharedTemplatePageReqVO reqVO);

    MaxLabelSharedTemplateDO getAdmin(Long id);

    void approve(Long id, Long operatorId);

    void reject(Long id, Long operatorId, String reason);

    void offline(Long id, Long operatorId);

}
