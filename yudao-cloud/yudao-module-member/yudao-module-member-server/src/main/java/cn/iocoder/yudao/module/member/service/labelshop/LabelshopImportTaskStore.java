package cn.iocoder.yudao.module.member.service.labelshop;

import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportTaskDO;
import cn.iocoder.yudao.module.member.dal.mysql.labelshop.LabelshopImportTaskMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_LABELSHOP_IMPORT_RUNNING;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_LABELSHOP_TASK_NOT_FOUND;

/**
 * 导入任务存储：创建、推进、终态。
 */
@Component
public class LabelshopImportTaskStore {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_FAILED = 3;
    public static final int STATUS_INTERRUPTED = 4;

    private final LabelshopImportTaskMapper taskMapper;

    public LabelshopImportTaskStore(LabelshopImportTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    public LabelshopImportTaskDO create(LabelshopImportTaskDO task) {
        try {
            taskMapper.insert(task);
            return task;
        } catch (DuplicateKeyException ex) {
            throw exception(MEMBER_LABELSHOP_IMPORT_RUNNING);
        }
    }

    public LabelshopImportTaskDO require(Long taskId) {
        LabelshopImportTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw exception(MEMBER_LABELSHOP_TASK_NOT_FOUND);
        }
        return task;
    }

    public void markRunning(Long taskId, String phase) {
        LocalDateTime now = LocalDateTime.now();
        taskMapper.updateById(new LabelshopImportTaskDO().setId(taskId)
                .setStatus(STATUS_RUNNING).setPhase(phase)
                .setStartedTime(now).setHeartbeatTime(now));
    }

    public void updatePhase(Long taskId, String phase) {
        taskMapper.updateById(new LabelshopImportTaskDO().setId(taskId)
                .setPhase(phase).setHeartbeatTime(LocalDateTime.now()));
    }

    public void updateCounters(Long taskId, int total, int processed, int success, int skip, int fail,
                               long personalBytes, long enterpriseBytes, String phase) {
        taskMapper.updateById(new LabelshopImportTaskDO().setId(taskId)
                .setTotalCount(total).setProcessedCount(processed)
                .setSuccessCount(success).setSkipCount(skip).setFailCount(fail)
                .setPersonalBytes(personalBytes).setEnterpriseBytes(enterpriseBytes)
                .setPhase(phase).setHeartbeatTime(LocalDateTime.now()));
    }

    public void setFolder(Long taskId, Long folderId, String folderName) {
        taskMapper.updateById(new LabelshopImportTaskDO().setId(taskId)
                .setFolderId(folderId).setFolderName(folderName));
    }

    @Transactional(rollbackFor = Exception.class)
    public void finish(Long taskId, int status, String errorMessage) {
        LabelshopImportTaskDO update = new LabelshopImportTaskDO().setId(taskId)
                .setStatus(status).setFinishedTime(LocalDateTime.now())
                .setHeartbeatTime(LocalDateTime.now());
        if (errorMessage != null) {
            update.setErrorMessage(errorMessage);
        }
        taskMapper.updateById(update);
        taskMapper.clearRunningOrg(taskId);
        taskMapper.clearRunningMember(taskId);
    }

}
