package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairRequestDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customerservice.CustomerServiceRepairRequestMapper;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceActionConstants;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceOperatorTypeEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceSourceEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceTargetTypeEnum;
import cn.iocoder.yudao.module.trade.service.customerservice.bo.CustomerServiceRepairCreateReqBO;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMER_SERVICE_REPAIR_LIMIT;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMER_SERVICE_REPAIR_NOT_EXISTS;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMER_SERVICE_REPAIR_STATUS_INVALID;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMER_SERVICE_TICKET_INVALID;
import static cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceLogRecordConstants.*;

/**
 * 客户服务 - 维修申请 Service 实现（021 / US71）
 */
@Service
public class CustomerServiceRepairRequestServiceImpl implements CustomerServiceRepairRequestService {

    @Resource
    private CustomerServiceRepairRequestMapper customerServiceRepairRequestMapper;
    @Resource
    private CustomerServiceFileValidator customerServiceFileValidator;
    @Resource
    private CustomerServiceLogService customerServiceLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRepair(CustomerServiceRepairCreateReqBO reqBO) {
        validateCreate(reqBO);
        // 同一客户最多同时保留 10 台在修设备（完成/取消后可继续提交）
        Long activeCount = customerServiceRepairRequestMapper.selectCountByMemberIdAndStatusIn(
                reqBO.getMemberId(), List.of(
                        CustomerServiceRepairStatusEnum.PENDING_CONTACT.getStatus(),
                        CustomerServiceRepairStatusEnum.CONTACTED.getStatus(),
                        CustomerServiceRepairStatusEnum.ORDERED.getStatus(),
                        CustomerServiceRepairStatusEnum.SHIPPED.getStatus(),
                        CustomerServiceRepairStatusEnum.REPAIRING.getStatus()));
        if (activeCount != null && activeCount >= 10) {
            throw exception(CUSTOMER_SERVICE_REPAIR_LIMIT);
        }
        CustomerServiceRepairRequestDO request = CustomerServiceRepairRequestDO.builder()
                .memberId(reqBO.getMemberId())
                .deviceModel(reqBO.getDeviceModel().trim())
                .faultDesc(reqBO.getFaultDesc().trim())
                .contactMobile(reqBO.getContactMobile().trim())
                .receiverName(reqBO.getReceiverName().trim())
                .receiverMobile(reqBO.getReceiverMobile().trim())
                .receiverArea(reqBO.getReceiverArea().trim())
                .receiverDetail(reqBO.getReceiverDetail().trim())
                .images(CollUtil.isEmpty(reqBO.getImages()) ? null : JsonUtils.toJsonString(reqBO.getImages()))
                .status(CustomerServiceRepairStatusEnum.PENDING_CONTACT.getStatus())
                .source(reqBO.getSource() == null ? CustomerServiceSourceEnum.MEMBER.getSource() : reqBO.getSource())
                .creatorId(reqBO.getCreatorId())
                .build();
        customerServiceRepairRequestMapper.insert(request);
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.REPAIR_REQUEST.getType(), request.getId(),
                CustomerServiceActionConstants.CREATE, null, request.getStatus(), "提交维修申请",
                operatorType(reqBO.getSource()), reqBO.getCreatorId());
        return request.getId();
    }

    @Override
    public PageResult<CustomerServiceRepairRequestDO> getMyRepairPage(Long memberId, PageParam pageParam) {
        return customerServiceRepairRequestMapper.selectPage(memberId, null, null, null,
                null, null, pageParam);
    }

    @Override
    public CustomerServiceRepairRequestDO getMyRepair(Long memberId, Long id) {
        CustomerServiceRepairRequestDO request = customerServiceRepairRequestMapper.selectById(id);
        if (request == null || !request.getMemberId().equals(memberId)) {
            throw exception(CUSTOMER_SERVICE_REPAIR_NOT_EXISTS);
        }
        return request;
    }

    @Override
    public PageResult<CustomerServiceRepairRequestDO> getRepairPage(Integer status, Integer source,
                                                                    Collection<Long> memberIds, String keyword,
                                                                    LocalDateTime[] createTime, PageParam pageParam) {
        return customerServiceRepairRequestMapper.selectPage(null, status, source, memberIds,
                StrUtil.trimToNull(keyword), createTime, pageParam);
    }

    @Override
    public CustomerServiceRepairRequestDO getRepair(Long id) {
        CustomerServiceRepairRequestDO request = customerServiceRepairRequestMapper.selectById(id);
        if (request == null) {
            throw exception(CUSTOMER_SERVICE_REPAIR_NOT_EXISTS);
        }
        return request;
    }

    @Override
    public List<CustomerServiceRepairRequestDO> getRepairs(Collection<Long> ids) {
        return CollUtil.isEmpty(ids) ? List.of() : customerServiceRepairRequestMapper.selectBatchIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CUSTOMER_SERVICE_TYPE, subType = REPAIR_STATUS_SUB_TYPE,
            success = REPAIR_STATUS_SUCCESS, bizNo = "{{#id}}")
    public void updateStatus(Long id, Integer status, String remark, Long adminUserId) {
        CustomerServiceRepairRequestDO request = getRepair(id);
        if (!isAllowedTransition(request.getStatus(), status)) {
            throw exception(CUSTOMER_SERVICE_REPAIR_STATUS_INVALID);
        }
        CustomerServiceRepairRequestDO update = new CustomerServiceRepairRequestDO();
        update.setId(id);
        update.setStatus(status);
        customerServiceRepairRequestMapper.updateById(update);
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.REPAIR_REQUEST.getType(), id,
                CustomerServiceRepairStatusEnum.REPAIRING.getStatus().equals(status)
                        ? CustomerServiceActionConstants.REPAIRING : CustomerServiceActionConstants.COMPLETE,
                request.getStatus(), status, remark,
                CustomerServiceOperatorTypeEnum.ADMIN.getType(), adminUserId);
    }

    private static boolean isAllowedTransition(Integer from, Integer to) {
        return (CustomerServiceRepairStatusEnum.SHIPPED.getStatus().equals(from)
                && CustomerServiceRepairStatusEnum.REPAIRING.getStatus().equals(to))
                || (CustomerServiceRepairStatusEnum.REPAIRING.getStatus().equals(from)
                && CustomerServiceRepairStatusEnum.COMPLETED.getStatus().equals(to));
    }

    private void validateCreate(CustomerServiceRepairCreateReqBO reqBO) {
        if (reqBO.getMemberId() == null
                || StrUtil.isBlank(reqBO.getDeviceModel()) || reqBO.getDeviceModel().length() > 100
                || StrUtil.isBlank(reqBO.getFaultDesc()) || reqBO.getFaultDesc().length() > 1000
                || StrUtil.isBlank(reqBO.getContactMobile())
                || !reqBO.getContactMobile().matches("^1\\d{10}$")
                || StrUtil.isBlank(reqBO.getReceiverName()) || reqBO.getReceiverName().length() > 50
                || StrUtil.isBlank(reqBO.getReceiverMobile()) || reqBO.getReceiverMobile().length() > 20
                || StrUtil.isBlank(reqBO.getReceiverArea()) || reqBO.getReceiverArea().length() > 100
                || StrUtil.isBlank(reqBO.getReceiverDetail()) || reqBO.getReceiverDetail().length() > 255) {
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
