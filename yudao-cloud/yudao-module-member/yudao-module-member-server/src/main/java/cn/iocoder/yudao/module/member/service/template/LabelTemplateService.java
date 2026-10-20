package cn.iocoder.yudao.module.member.service.template;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppLabelTemplateSaveReqVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppSharedTemplatePageReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelLabelTemplateDO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelSharedTemplateDO;

public interface LabelTemplateService {

    PageResult<MaxLabelLabelTemplateDO> getPage(Long memberId, String keyword, PageParam pageParam);

    MaxLabelLabelTemplateDO getOwned(Long memberId, Long id);

    MaxLabelLabelTemplateDO getById(Long id);

    MaxLabelLabelTemplateDO getContent(Long memberId, Long id);

    Long create(Long memberId, AppLabelTemplateSaveReqVO reqVO);

    void update(Long memberId, AppLabelTemplateSaveReqVO reqVO);

    void delete(Long memberId, Long id);

    PageResult<MaxLabelSharedTemplateDO> getSharedPage(AppSharedTemplatePageReqVO reqVO);

    MaxLabelSharedTemplateDO getSharedContent(Long id);

    void requireActiveSubscription(Long memberId);

}
