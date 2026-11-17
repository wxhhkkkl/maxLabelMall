package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceTicketDO;
import cn.iocoder.yudao.module.trade.service.customerservice.bo.CustomerServiceTicketCreateReqBO;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * 客户服务 - 投诉咨询 Service（021 / US70）
 */
public interface CustomerServiceTicketService {

    /** 创建工单；C 端自助与客服代建共用，60 秒内相同内容只生成一条 */
    Long createTicket(CustomerServiceTicketCreateReqBO reqBO);

    /** C 端：我的工单分页 */
    PageResult<CustomerServiceTicketDO> getMyTicketPage(Long memberId, PageParam pageParam);

    /** C 端：我的工单详情（非本人视为不存在） */
    CustomerServiceTicketDO getMyTicket(Long memberId, Long id);

    /** 管理端：工单分页（memberIds 由调用方按关键字解析） */
    PageResult<CustomerServiceTicketDO> getTicketPage(Integer type, Integer status, Integer source,
                                                      Collection<Long> memberIds, String keyword,
                                                      LocalDateTime[] createTime, PageParam pageParam);

    /** 管理端：工单详情 */
    CustomerServiceTicketDO getTicket(Long id);

    /** 管理端：填写处理说明并标记已处理 */
    void handleTicket(Long id, String handleRemark, Long adminUserId);

}
