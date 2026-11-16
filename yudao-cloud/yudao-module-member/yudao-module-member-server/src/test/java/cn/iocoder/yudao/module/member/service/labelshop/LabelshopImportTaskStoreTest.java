package cn.iocoder.yudao.module.member.service.labelshop;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportTaskDO;
import cn.iocoder.yudao.module.member.dal.mysql.labelshop.LabelshopImportTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_LABELSHOP_IMPORT_RUNNING;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_LABELSHOP_TASK_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LabelshopImportTaskStoreTest {

    @Mock
    private LabelshopImportTaskMapper taskMapper;

    @InjectMocks
    private LabelshopImportTaskStore taskStore;

    @Test
    void create_mapsDuplicateRunningOrgToRunningError() {
        when(taskMapper.insert(any(LabelshopImportTaskDO.class)))
                .thenThrow(new DuplicateKeyException("uk_running_org"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> taskStore.create(new LabelshopImportTaskDO()));

        assertEquals(MEMBER_LABELSHOP_IMPORT_RUNNING.getCode(), exception.getCode());
    }

    @Test
    void require_throwsWhenTaskMissing() {
        when(taskMapper.selectById(9L)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> taskStore.require(9L));

        assertEquals(MEMBER_LABELSHOP_TASK_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void finish_updatesStatusAndClearsRunningOrg() {
        taskStore.finish(9L, LabelshopImportTaskStore.STATUS_FAILED, "失败");

        verify(taskMapper).updateById(any(LabelshopImportTaskDO.class));
        verify(taskMapper).clearRunningOrg(9L);
    }

}
