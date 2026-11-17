package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairRequestDO;
import cn.iocoder.yudao.module.trade.service.customerservice.bo.CustomerServiceRepairCreateReqBO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 客户服务 - 维修申请 Service（021 / US71）
 *
 * 取消与开单编排在 US72 的订单 service，避免 US71 依赖后续故事。
 */
public interface CustomerServiceRepairRequestService {

    Long createRepair(CustomerServiceRepairCreateReqBO reqBO);

    PageResult<CustomerServiceRepairRequestDO> getMyRepairPage(Long memberId, PageParam pageParam);

    CustomerServiceRepairRequestDO getMyRepair(Long memberId, Long id);

    PageResult<CustomerServiceRepairRequestDO> getRepairPage(Integer status, Integer source,
                                                             Collection<Long> memberIds, String keyword,
                                                             LocalDateTime[] createTime, PageParam pageParam);

    CustomerServiceRepairRequestDO getRepair(Long id);

    List<CustomerServiceRepairRequestDO> getRepairs(Collection<Long> ids);

    /** 管理端推进维修状态：仅允许 30→40、40→50 */
    void updateStatus(Long id, Integer status, String remark, Long adminUserId);

}
