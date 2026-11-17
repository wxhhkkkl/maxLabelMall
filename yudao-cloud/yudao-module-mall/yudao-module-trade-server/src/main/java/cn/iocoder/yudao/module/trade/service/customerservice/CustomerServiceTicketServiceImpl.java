package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceTicketDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customerservice.CustomerServiceTicketMapper;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceActionConstants;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceOperatorTypeEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceSourceEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceTargetTypeEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceTicketStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceTicketTypeEnum;
import cn.iocoder.yudao.module.trade.service.customerservice.bo.CustomerServiceTicketCreateReqBO;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceLogRecordConstants.*;

/**
 * 客户服务 - 投诉咨询 Service 实现（021 / US70）
 */
@Service
public class CustomerServiceTicketServiceImpl implements CustomerServiceTicketService {

    private static final int DEDUPE_SECONDS = 60;

    @Resource
    private CustomerServiceTicketMapper customerServiceTicketMapper;
    @Resource
    private CustomerServiceFileValidator customerServiceFileValidator;
    @Resource
    private CustomerServiceLogService customerServiceLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTicket(CustomerServiceTicketCreateReqBO reqBO) {
        validateCreate(reqBO);
        // 60 秒内相同类型 + 相同内容只生成一条（Q / FR-562）
        CustomerServiceTicketDO duplicated = customerServiceTicketMapper.selectRecentSame(
                reqBO.getMemberId(), reqBO.getType(), reqBO.getContent().trim(),
                LocalDateTime.now().minusSeconds(DEDUPE_SECONDS));
        if (duplicated != null) {
            return duplicated.getId();
        }
        // 同一客户最多同时保留 10 条待处理投诉咨询（2026-11-17 所有者口径）
        Long pendingCount = customerServiceTicketMapper.selectCountByMemberIdAndStatus(
                reqBO.getMemberId(), CustomerServiceTicketStatusEnum.PENDING.getStatus());
        if (pendingCount != null && pendingCount >= 10) {
            throw exception(CUSTOMER_SERVICE_TICKET_LIMIT);
        }

        CustomerServiceTicketDO ticket = CustomerServiceTicketDO.builder()
                .memberId(reqBO.getMemberId())
                .type(reqBO.getType())
                .content(reqBO.getContent().trim())
                .contactMobile(reqBO.getContactMobile().trim())
                .images(CollUtil.isEmpty(reqBO.getImages()) ? null : JsonUtils.toJsonString(reqBO.getImages()))
                .status(CustomerServiceTicketStatusEnum.PENDING.getStatus())
                .source(reqBO.getSource() == null ? CustomerServiceSourceEnum.MEMBER.getSource() : reqBO.getSource())
                .creatorId(reqBO.getCreatorId())
                .build();
        customerServiceTicketMapper.insert(ticket);
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.TICKET.getType(), ticket.getId(),
                CustomerServiceActionConstants.CREATE, null, CustomerServiceTicketStatusEnum.PENDING.getStatus(),
                "提交投诉咨询", operatorType(reqBO.getSource()), reqBO.getCreatorId());
        return ticket.getId();
    }

    @Override
    public PageResult<CustomerServiceTicketDO> getMyTicketPage(Long memberId, PageParam pageParam) {
        return customerServiceTicketMapper.selectPage(memberId, null, null, null, null,
                null, null, pageParam);
    }

    @Override
    public CustomerServiceTicketDO getMyTicket(Long memberId, Long id) {
        CustomerServiceTicketDO ticket = customerServiceTicketMapper.selectById(id);
        if (ticket == null || !ticket.getMemberId().equals(memberId)) {
            throw exception(CUSTOMER_SERVICE_TICKET_NOT_EXISTS);
        }
        return ticket;
    }

    @Override
    public PageResult<CustomerServiceTicketDO> getTicketPage(Integer type, Integer status, Integer source,
                                                             Collection<Long> memberIds, String keyword,
                                                             LocalDateTime[] createTime, PageParam pageParam) {
        return customerServiceTicketMapper.selectPage(null, type, status, source, memberIds,
                StrUtil.trimToNull(keyword), createTime, pageParam);
    }

    @Override
    public CustomerServiceTicketDO getTicket(Long id) {
        CustomerServiceTicketDO ticket = customerServiceTicketMapper.selectById(id);
        if (ticket == null) {
            throw exception(CUSTOMER_SERVICE_TICKET_NOT_EXISTS);
        }
        return ticket;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CUSTOMER_SERVICE_TYPE, subType = TICKET_HANDLE_SUB_TYPE,
            success = TICKET_HANDLE_SUCCESS, bizNo = "{{#id}}")
    public void handleTicket(Long id, String handleRemark, Long adminUserId) {
        if (StrUtil.isBlank(handleRemark) || handleRemark.length() > 500) {
            throw exception(CUSTOMER_SERVICE_TICKET_HANDLE_REMARK_REQUIRED);
        }
        CustomerServiceTicketDO ticket = getTicket(id);
        if (!CustomerServiceTicketStatusEnum.PENDING.getStatus().equals(ticket.getStatus())) {
            throw exception(CUSTOMER_SERVICE_TICKET_HANDLED);
        }
        CustomerServiceTicketDO update = new CustomerServiceTicketDO();
        update.setId(id);
        update.setStatus(CustomerServiceTicketStatusEnum.HANDLED.getStatus());
        update.setHandleRemark(handleRemark.trim());
        update.setHandlerId(adminUserId);
        update.setHandleTime(LocalDateTime.now());
        customerServiceTicketMapper.updateById(update);
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.TICKET.getType(), id,
                CustomerServiceActionConstants.HANDLE, CustomerServiceTicketStatusEnum.PENDING.getStatus(),
                CustomerServiceTicketStatusEnum.HANDLED.getStatus(), handleRemark.trim(),
                CustomerServiceOperatorTypeEnum.ADMIN.getType(), adminUserId);
    }

    private void validateCreate(CustomerServiceTicketCreateReqBO reqBO) {
        if (reqBO.getMemberId() == null
                || !CustomerServiceTicketTypeEnum.COMPLAINT.getType().equals(reqBO.getType())
                && !CustomerServiceTicketTypeEnum.CONSULT.getType().equals(reqBO.getType())
                || StrUtil.isBlank(reqBO.getContent()) || reqBO.getContent().length() > 1000
                || StrUtil.isBlank(reqBO.getContactMobile())
                || !reqBO.getContactMobile().matches("^1\\d{10}$")) {
            throw exception(CUSTOMER_SERVICE_TICKET_INVALID);
        }
        customerServiceFileValidator.validate(reqBO.getImages());
    }

    private static Integer operatorType(Integer source) {
        return CustomerServiceSourceEnum.ADMIN.getSource().equals(source)
                ? CustomerServiceOperatorTypeEnum.ADMIN.getType()
                : CustomerServiceOperatorTypeEnum.MEMBER.getType();
    }

}
