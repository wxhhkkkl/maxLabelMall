package cn.iocoder.yudao.module.member.dal.mysql.labelshop;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportTaskDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * MaxLabel LabelShop 导入任务 Mapper
 */
@Mapper
public interface LabelshopImportTaskMapper extends BaseMapperX<LabelshopImportTaskDO> {

    default LabelshopImportTaskDO selectLatestByOrg(Long orgId) {
        return selectOne(new LambdaQueryWrapperX<LabelshopImportTaskDO>()
                .eq(LabelshopImportTaskDO::getOrgId, orgId)
                .orderByDesc(LabelshopImportTaskDO::getId)
                .last("LIMIT 1"));
    }

    default LabelshopImportTaskDO selectRunningByOrg(Long orgId) {
        return selectOne(new LambdaQueryWrapperX<LabelshopImportTaskDO>()
                .eq(LabelshopImportTaskDO::getRunningOrgId, orgId)
                .in(LabelshopImportTaskDO::getStatus, 0, 1)
                .last("LIMIT 1"));
    }

    default PageResult<LabelshopImportTaskDO> selectPageByOrg(Long orgId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<LabelshopImportTaskDO>()
                .eq(LabelshopImportTaskDO::getOrgId, orgId)
                .orderByDesc(LabelshopImportTaskDO::getId));
    }

    default LabelshopImportTaskDO selectLatestByMember(Long memberId) {
        return selectOne(new LambdaQueryWrapperX<LabelshopImportTaskDO>()
                .eq(LabelshopImportTaskDO::getSubjectType, LabelshopImportTaskDO.SUBJECT_MEMBER)
                .eq(LabelshopImportTaskDO::getOwnerMemberId, memberId)
                .orderByDesc(LabelshopImportTaskDO::getId)
                .last("LIMIT 1"));
    }

    default LabelshopImportTaskDO selectRunningByMember(Long memberId) {
        return selectOne(new LambdaQueryWrapperX<LabelshopImportTaskDO>()
                .eq(LabelshopImportTaskDO::getRunningMemberId, memberId)
                .in(LabelshopImportTaskDO::getStatus, 0, 1)
                .last("LIMIT 1"));
    }

    default int clearRunningOrg(Long taskId) {
        return update(null, new LambdaUpdateWrapper<LabelshopImportTaskDO>()
                .eq(LabelshopImportTaskDO::getId, taskId)
                .set(LabelshopImportTaskDO::getRunningOrgId, null));
    }

    default int clearRunningMember(Long taskId) {
        return update(null, new LambdaUpdateWrapper<LabelshopImportTaskDO>()
                .eq(LabelshopImportTaskDO::getId, taskId)
                .set(LabelshopImportTaskDO::getRunningMemberId, null));
    }

    /**
     * 跨租户把残留的待执行/进行中任务标记为中断；调用方需包 TenantUtils.executeIgnore。
     */
    default int markActiveInterrupted(String message) {
        return update(null, new LambdaUpdateWrapper<LabelshopImportTaskDO>()
                .in(LabelshopImportTaskDO::getStatus, 0, 1)
                .set(LabelshopImportTaskDO::getStatus, 4)
                .set(LabelshopImportTaskDO::getRunningOrgId, null)
                .set(LabelshopImportTaskDO::getRunningMemberId, null)
                .set(LabelshopImportTaskDO::getErrorMessage, message)
                .set(LabelshopImportTaskDO::getFinishedTime, LocalDateTime.now()));
    }

}
