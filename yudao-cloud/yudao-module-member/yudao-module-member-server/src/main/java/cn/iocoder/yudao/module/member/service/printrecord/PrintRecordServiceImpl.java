package cn.iocoder.yudao.module.member.service.printrecord;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.printrecord.vo.AdminPrintRecordPageReqVO;
import cn.iocoder.yudao.module.member.controller.app.printrecord.vo.AppPrintRecordCreateReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.printrecord.MaxLabelPrintRecordDO;
import cn.iocoder.yudao.module.member.dal.mysql.printrecord.MaxLabelPrintRecordMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_PRINT_RECORD_INVALID;

@Service
@Validated
public class PrintRecordServiceImpl implements PrintRecordService {

    private static final Set<String> MODES = Set.of("driver", "command");
    private static final Set<String> STATUSES = Set.of(
            "completed", "submitted", "partial", "failed", "canceled", "unknown");

    @Resource
    private MaxLabelPrintRecordMapper printRecordMapper;

    @Override
    public Long create(Long memberId, AppPrintRecordCreateReqVO reqVO) {
        validate(reqVO);
        MaxLabelPrintRecordDO existing = printRecordMapper.selectByMemberIdAndRecordUid(
                memberId, reqVO.getRecordUid());
        if (existing != null) {
            return existing.getId();
        }
        MaxLabelPrintRecordDO record = MaxLabelPrintRecordDO.builder()
                .memberId(memberId)
                .deviceId(reqVO.getDeviceId().trim())
                .recordUid(reqVO.getRecordUid().trim())
                .printTime(reqVO.getPrintTime())
                .templateTitle(reqVO.getTemplateTitle().trim())
                .mode(reqVO.getMode())
                .count(reqVO.getCount())
                .copies(reqVO.getCopies())
                .physicalCount(reqVO.getPhysicalCount())
                .sentCount(reqVO.getSentCount())
                .status(reqVO.getStatus())
                .printer(reqVO.getPrinter() == null ? "" : reqVO.getPrinter().trim())
                .build();
        try {
            printRecordMapper.insert(record);
        } catch (DuplicateKeyException duplicate) {
            return printRecordMapper.selectByMemberIdAndRecordUid(memberId, reqVO.getRecordUid()).getId();
        }
        return record.getId();
    }

    @Override
    public PageResult<MaxLabelPrintRecordDO> getPage(AdminPrintRecordPageReqVO reqVO) {
        return printRecordMapper.selectPageByMemberId(reqVO);
    }

    private void validate(AppPrintRecordCreateReqVO reqVO) {
        if (!MODES.contains(reqVO.getMode()) || !STATUSES.contains(reqVO.getStatus())) {
            throw exception(MEMBER_PRINT_RECORD_INVALID);
        }
        if (!isUuid(reqVO.getRecordUid()) || !isUuid(reqVO.getDeviceId())) {
            throw exception(MEMBER_PRINT_RECORD_INVALID);
        }
        if (reqVO.getCount() < 0 || reqVO.getCopies() < 0
                || (reqVO.getPhysicalCount() != null && reqVO.getPhysicalCount() < 0)
                || (reqVO.getSentCount() != null && reqVO.getSentCount() < 0)) {
            throw exception(MEMBER_PRINT_RECORD_INVALID);
        }
    }

    private boolean isUuid(String value) {
        return value != null && value.length() == 36;
    }

}
