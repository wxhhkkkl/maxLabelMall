package cn.iocoder.yudao.module.member.service.labelshop;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportItemDO;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportTaskDO;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopPrecheck;

/**
 * MaxLabel LabelShop 模板导入 Service。
 */
public interface LabelshopImportService {

    Long start(Long orgId, String account, String password, Boolean authorized,
               Long operatorId, String operatorName);

    /** 管理端替个人会员代导：只导「我的模板库」到该会员的个人空间 */
    Long startForMember(Long memberId, String account, String password, Boolean authorized,
                        Long operatorId, String operatorName);

    LabelshopImportTaskDO getTask(Long taskId);

    LabelshopImportTaskDO getLatest(Long orgId);

    LabelshopImportTaskDO getLatestForMember(Long memberId);

    PageResult<LabelshopImportItemDO> getItems(Long taskId, Integer status, Integer library,
                                               PageParam pageParam);

    LabelshopPrecheck getPrecheck(Long orgId);

    LabelshopPrecheck getMemberPrecheck(Long memberId);

}
