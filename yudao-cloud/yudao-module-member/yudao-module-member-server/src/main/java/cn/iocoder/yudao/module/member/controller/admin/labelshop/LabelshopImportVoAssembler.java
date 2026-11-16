package cn.iocoder.yudao.module.member.controller.admin.labelshop;

import cn.iocoder.yudao.module.member.controller.admin.labelshop.vo.AdminLabelshopImportItemRespVO;
import cn.iocoder.yudao.module.member.controller.admin.labelshop.vo.AdminLabelshopImportLibraryStatVO;
import cn.iocoder.yudao.module.member.controller.admin.labelshop.vo.AdminLabelshopImportPrecheckRespVO;
import cn.iocoder.yudao.module.member.controller.admin.labelshop.vo.AdminLabelshopImportTaskRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportItemDO;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportTaskDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.mysql.labelshop.LabelshopImportItemMapper;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopPrecheck;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgService;
import cn.iocoder.yudao.module.member.service.share.ShareSpaceService;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.member.service.share.ShareConstants.ENTERPRISE_BYTES;

/**
 * 企业 / 会员导入共用的响应转换。
 */
@Component
public class LabelshopImportVoAssembler {

    private static final int[] LIBRARY_ORDER = {12, 13, 1};

    @Resource
    private MaxLabelOrgService orgService;
    @Resource
    private ShareSpaceService spaceService;
    @Resource
    private LabelshopImportItemMapper itemMapper;

    public AdminLabelshopImportPrecheckRespVO toPrecheckVO(LabelshopPrecheck precheck) {
        AdminLabelshopImportPrecheckRespVO vo = new AdminLabelshopImportPrecheckRespVO();
        vo.setOrgId(precheck.getOrgId());
        vo.setOrgName(precheck.getOrgName());
        vo.setOwnerMemberId(precheck.getOwnerMemberId());
        vo.setOwnerNickname(precheck.getOwnerNickname());
        vo.setOwnerMobile(precheck.getOwnerMobileMasked());
        vo.setOrgStatus(precheck.getOrgStatus());
        vo.setLicenseActive(precheck.getLicenseActive());
        vo.setLicenseEndDate(precheck.getLicenseEndDate());
        vo.setSeatCount(precheck.getSeatCount());
        vo.setFolderName(precheck.getFolderName());
        vo.setPersonalEdition(precheck.getPersonalEdition());
        vo.setPersonalUsedBytes(precheck.getPersonalUsedBytes());
        vo.setPersonalQuotaBytes(precheck.getPersonalQuotaBytes());
        vo.setEnterpriseUsedBytes(precheck.getEnterpriseUsedBytes());
        vo.setEnterpriseQuotaBytes(precheck.getEnterpriseQuotaBytes());
        vo.setRunningTaskId(precheck.getRunningTaskId());
        return vo;
    }

    public AdminLabelshopImportTaskRespVO toTaskVO(LabelshopImportTaskDO task) {
        AdminLabelshopImportTaskRespVO vo = new AdminLabelshopImportTaskRespVO();
        vo.setId(task.getId());
        vo.setSubjectType(task.getSubjectType());
        vo.setOrgId(task.getOrgId());
        MaxLabelOrgDO org = task.getOrgId() == null ? null : orgService.getOrg(task.getOrgId());
        vo.setOrgName(org == null ? null : org.getName());
        vo.setOwnerMemberId(task.getOwnerMemberId());
        vo.setFolderId(task.getFolderId());
        vo.setFolderName(task.getFolderName());
        vo.setSourceAccountMasked(task.getSourceAccountMasked());
        vo.setStatus(task.getStatus());
        vo.setPhase(task.getPhase());
        vo.setTotalCount(task.getTotalCount());
        vo.setProcessedCount(task.getProcessedCount());
        vo.setSuccessCount(task.getSuccessCount());
        vo.setSkipCount(task.getSkipCount());
        vo.setFailCount(task.getFailCount());
        vo.setPersonalBytes(task.getPersonalBytes());
        vo.setEnterpriseBytes(task.getEnterpriseBytes());
        long personalUsed = spaceService.getPersonalUsedBytes(task.getOwnerMemberId());
        long personalQuota = spaceService.getPersonalQuotaBytes(task.getOwnerMemberId());
        vo.setPersonalUsedBytes(personalUsed);
        vo.setPersonalQuotaBytes(personalQuota);
        vo.setPersonalExceeded(personalUsed > personalQuota);
        if (task.getOrgId() == null) {
            vo.setEnterpriseUsedBytes(0L);
            vo.setEnterpriseQuotaBytes(0L);
            vo.setEnterpriseExceeded(false);
        } else {
            long enterpriseUsed = spaceService.getEnterpriseUsedBytes(task.getOrgId());
            vo.setEnterpriseUsedBytes(enterpriseUsed);
            vo.setEnterpriseQuotaBytes(ENTERPRISE_BYTES);
            vo.setEnterpriseExceeded(enterpriseUsed > ENTERPRISE_BYTES);
        }
        vo.setLibraries(libraryStats(task.getId()));
        vo.setUncleaned(true);
        vo.setArchiveJsonCount(itemMapper.countByTaskAndFormat(task.getId(), "cloud-json"));
        vo.setOperatorId(task.getOperatorId());
        vo.setOperatorName(task.getOperatorName());
        vo.setStartedTime(task.getStartedTime());
        vo.setFinishedTime(task.getFinishedTime());
        vo.setErrorMessage(task.getErrorMessage());
        return vo;
    }

    public AdminLabelshopImportItemRespVO toItemVO(LabelshopImportItemDO item) {
        AdminLabelshopImportItemRespVO vo = new AdminLabelshopImportItemRespVO();
        vo.setId(item.getId());
        vo.setSourceLibrary(item.getSourceLibrary());
        vo.setSourceCategory(item.getSourceCategory());
        vo.setSourceName(item.getSourceName());
        vo.setTargetScope(item.getTargetScope());
        vo.setFormat(item.getFormat());
        vo.setEditable(item.getEditable());
        vo.setSizeBytes(item.getSizeBytes());
        vo.setStatus(item.getStatus());
        vo.setErrorMessage(item.getErrorMessage());
        return vo;
    }

    public List<AdminLabelshopImportLibraryStatVO> libraryStats(Long taskId) {
        Map<Integer, AdminLabelshopImportLibraryStatVO> stats = new LinkedHashMap<>();
        for (int library : LIBRARY_ORDER) {
            AdminLabelshopImportLibraryStatVO vo = new AdminLabelshopImportLibraryStatVO();
            vo.setLibrary(library);
            stats.put(library, vo);
        }
        for (Map<String, Object> row : itemMapper.selectLibraryStats(taskId)) {
            Object libraryValue = row.get("source_library");
            Object statusValue = row.get("status");
            Object countValue = row.get("cnt");
            if (!(libraryValue instanceof Number) || !(statusValue instanceof Number)
                    || !(countValue instanceof Number)) {
                continue;
            }
            int library = ((Number) libraryValue).intValue();
            int status = ((Number) statusValue).intValue();
            int count = ((Number) countValue).intValue();
            AdminLabelshopImportLibraryStatVO vo = stats.computeIfAbsent(library, key -> {
                AdminLabelshopImportLibraryStatVO created = new AdminLabelshopImportLibraryStatVO();
                created.setLibrary(key);
                return created;
            });
            vo.setTotal(vo.getTotal() + count);
            switch (status) {
                case 1 -> vo.setSuccess(vo.getSuccess() + count);
                case 2 -> vo.setSkip(vo.getSkip() + count);
                case 3 -> vo.setFail(vo.getFail() + count);
                default -> {
                }
            }
        }
        return new ArrayList<>(stats.values());
    }

}
