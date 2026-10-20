package cn.iocoder.yudao.module.member.service.printrecord;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.member.controller.app.printrecord.vo.AppPrintRecordCreateReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.printrecord.MaxLabelPrintRecordDO;
import cn.iocoder.yudao.module.member.dal.mysql.printrecord.MaxLabelPrintRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_PRINT_RECORD_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PrintRecordServiceImplTest {

    @Mock
    private MaxLabelPrintRecordMapper printRecordMapper;
    @InjectMocks
    private PrintRecordServiceImpl printRecordService;

    private AppPrintRecordCreateReqVO request() {
        AppPrintRecordCreateReqVO req = new AppPrintRecordCreateReqVO();
        req.setRecordUid(UUID.randomUUID().toString());
        req.setDeviceId(UUID.randomUUID().toString());
        req.setPrintTime(LocalDateTime.now());
        req.setTemplateTitle("顺丰面单");
        req.setMode("driver");
        req.setCount(10);
        req.setCopies(1);
        req.setPhysicalCount(10L);
        req.setSentCount(10L);
        req.setStatus("completed");
        req.setPrinter("Zebra ZT410");
        return req;
    }

    @Test
    void createsRecordWithoutDataSnapshot() {
        AppPrintRecordCreateReqVO req = request();
        when(printRecordMapper.selectByMemberIdAndRecordUid(1001L, req.getRecordUid())).thenReturn(null);
        when(printRecordMapper.insert(any(MaxLabelPrintRecordDO.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, MaxLabelPrintRecordDO.class).setId(5L);
            return 1;
        });

        assertEquals(5L, printRecordService.create(1001L, req));
        ArgumentCaptor<MaxLabelPrintRecordDO> captor = ArgumentCaptor.forClass(MaxLabelPrintRecordDO.class);
        verify(printRecordMapper).insert(captor.capture());
        MaxLabelPrintRecordDO saved = captor.getValue();
        assertEquals("顺丰面单", saved.getTemplateTitle());
        assertEquals("completed", saved.getStatus());
        assertEquals("Zebra ZT410", saved.getPrinter());
    }

    @Test
    void duplicateRecordUidReturnsExistingId() {
        AppPrintRecordCreateReqVO req = request();
        when(printRecordMapper.selectByMemberIdAndRecordUid(1001L, req.getRecordUid()))
                .thenReturn(MaxLabelPrintRecordDO.builder().id(5L).build());
        assertEquals(5L, printRecordService.create(1001L, req));
        verify(printRecordMapper, never()).insert(any(MaxLabelPrintRecordDO.class));
    }

    @Test
    void rejectsInvalidModeAndStatus() {
        AppPrintRecordCreateReqVO req = request();
        req.setMode("unknown-mode");
        ServiceException exception = assertThrows(ServiceException.class,
                () -> printRecordService.create(1001L, req));
        assertEquals(MEMBER_PRINT_RECORD_INVALID.getCode(), exception.getCode());

        AppPrintRecordCreateReqVO badStatus = request();
        badStatus.setStatus("nope");
        assertThrows(ServiceException.class, () -> printRecordService.create(1001L, badStatus));
    }

    @Test
    void rejectsMalformedDeviceOrRecordId() {
        AppPrintRecordCreateReqVO req = request();
        req.setDeviceId("not-a-uuid");
        assertThrows(ServiceException.class, () -> printRecordService.create(1001L, req));
    }

}
