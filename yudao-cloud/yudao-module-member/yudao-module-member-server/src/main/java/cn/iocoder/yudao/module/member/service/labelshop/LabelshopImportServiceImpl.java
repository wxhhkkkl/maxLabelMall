package cn.iocoder.yudao.module.member.service.labelshop;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.member.controller.app.subscription.vo.AppMemberSubscriptionRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportItemDO;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportTaskDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgSeatDO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareFolderDO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareFolderMemberDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.labelshop.LabelshopImportItemMapper;
import cn.iocoder.yudao.module.member.dal.mysql.labelshop.LabelshopImportTaskMapper;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareContentMapper;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareFolderMapper;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareFolderMemberMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopEnumerationResult;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopPrecheck;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopSession;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopSourceItem;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopSts;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgLicenseService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgSeatService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgService;
import cn.iocoder.yudao.module.member.service.share.ShareFolderService;
import cn.iocoder.yudao.module.member.service.share.ShareProperties;
import cn.iocoder.yudao.module.member.service.share.ShareSpaceService;
import cn.iocoder.yudao.module.member.service.subscription.MemberSubscriptionService;
import jakarta.annotation.Resource;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.member.service.share.ShareConstants.*;

/**
 * LabelShop 模板导入 Service 实现。
 */
@Service
@Validated
public class LabelshopImportServiceImpl implements LabelshopImportService {

    private static final DateTimeFormatter FOLDER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String PHASE_LOGIN = "LOGIN";
    private static final String PHASE_LIST = "LIST";
    private static final String PHASE_IMPORT = "IMPORT";
    private static final String PHASE_FINISH = "FINISH";

    @Resource
    private MaxLabelOrgService orgService;
    @Resource
    private MaxLabelOrgSeatService seatService;
    @Resource
    private MaxLabelOrgLicenseService licenseService;
    @Resource
    private MemberUserMapper memberUserMapper;
    @Resource
    private MemberSubscriptionService subscriptionService;
    @Resource
    private MaxLabelShareFolderMapper folderMapper;
    @Resource
    private MaxLabelShareFolderMemberMapper folderMemberMapper;
    @Resource
    private MaxLabelShareContentMapper contentMapper;
    @Resource
    private LabelshopImportTaskMapper taskMapper;
    @Resource
    private LabelshopImportItemMapper itemMapper;
    @Resource
    private LabelshopImportTaskStore taskStore;
    @Resource
    private LabelshopClient client;
    @Resource
    private LabelshopSourceEnumerator enumerator;
    @Resource
    private LabelshopImportContentWriter contentWriter;
    @Resource
    private LabelshopImageImporter imageImporter;
    @Resource
    private ShareSpaceService spaceService;
    @Resource
    private ShareFolderService folderService;
    @Resource
    private CloudJsonToLsdxConverter cloudConverter;
    @Resource
    private ShareProperties shareProperties;
    @Resource
    private LabelshopImportProperties labelshopProperties;
    @Resource(name = "labelshopImportExecutor")
    private ThreadPoolTaskExecutor executor;

    @Override
    public Long start(Long orgId, String account, String password, Boolean authorized,
                      Long operatorId, String operatorName) {
        if (StrUtil.isBlank(account) || StrUtil.isBlank(password) || !Boolean.TRUE.equals(authorized)) {
            throw exception(MEMBER_LABELSHOP_CREDENTIAL_REQUIRED);
        }
        MaxLabelOrgDO org = orgService.getOrg(orgId);
        if (org == null) {
            throw exception(MEMBER_ORG_NOT_EXISTS);
        }
        if (!Objects.equals(org.getStatus(), 0)) {
            throw exception(MEMBER_ORG_DISABLED);
        }
        if (memberUserMapper.selectById(org.getOwnerMemberId()) == null) {
            throw exception(MEMBER_LABELSHOP_OWNER_MISSING);
        }
        if (licenseService.getUnexpiredList(orgId).isEmpty()) {
            throw exception(MEMBER_LABELSHOP_ORG_READ_ONLY);
        }
        if (taskMapper.selectRunningByOrg(orgId) != null) {
            throw exception(MEMBER_LABELSHOP_IMPORT_RUNNING);
        }
        LabelshopImportTaskDO task = LabelshopImportTaskDO.builder()
                .subjectType(LabelshopImportTaskDO.SUBJECT_ORG)
                .orgId(orgId)
                .ownerMemberId(org.getOwnerMemberId())
                .folderName(LocalDateTime.now().format(FOLDER_TIME) + "导入")
                .sourceAccountMasked(maskAccount(account))
                .status(LabelshopImportTaskStore.STATUS_PENDING)
                .runningOrgId(orgId)
                .operatorId(operatorId)
                .operatorName(StrUtil.nullToEmpty(operatorName))
                .build();
        taskStore.create(task);
        Long taskId = task.getId();
        Long tenantId = TenantContextHolder.getTenantId();
        try {
            List<Long> seatMemberIds = seatService.getActiveList(orgId).stream()
                    .map(MaxLabelOrgSeatDO::getMemberId).toList();
            executor.execute(() -> TenantUtils.execute(tenantId,
                    () -> runTask(taskId, account, password, seatMemberIds)));
        } catch (TaskRejectedException ex) {
            taskStore.finish(taskId, LabelshopImportTaskStore.STATUS_FAILED, "导入任务繁忙");
            throw exception(MEMBER_LABELSHOP_TASK_REJECTED);
        } catch (RuntimeException ex) {
            taskStore.finish(taskId, LabelshopImportTaskStore.STATUS_FAILED,
                    truncate("发起导入失败：" + ex.getMessage(), 500));
            throw ex;
        }
        return taskId;
    }

    @Override
    public Long startForMember(Long memberId, String account, String password, Boolean authorized,
                               Long operatorId, String operatorName) {
        if (StrUtil.isBlank(account) || StrUtil.isBlank(password) || !Boolean.TRUE.equals(authorized)) {
            throw exception(MEMBER_LABELSHOP_CREDENTIAL_REQUIRED);
        }
        if (memberUserMapper.selectById(memberId) == null) {
            throw exception(USER_NOT_EXISTS);
        }
        if (taskMapper.selectRunningByMember(memberId) != null) {
            throw exception(MEMBER_LABELSHOP_IMPORT_RUNNING);
        }
        LabelshopImportTaskDO task = LabelshopImportTaskDO.builder()
                .subjectType(LabelshopImportTaskDO.SUBJECT_MEMBER)
                .ownerMemberId(memberId)
                .sourceAccountMasked(maskAccount(account))
                .status(LabelshopImportTaskStore.STATUS_PENDING)
                .runningMemberId(memberId)
                .operatorId(operatorId)
                .operatorName(StrUtil.nullToEmpty(operatorName))
                .build();
        taskStore.create(task);
        Long taskId = task.getId();
        Long tenantId = TenantContextHolder.getTenantId();
        try {
            executor.execute(() -> TenantUtils.execute(tenantId,
                    () -> runTask(taskId, account, password, List.of())));
        } catch (TaskRejectedException ex) {
            taskStore.finish(taskId, LabelshopImportTaskStore.STATUS_FAILED, "导入任务繁忙");
            throw exception(MEMBER_LABELSHOP_TASK_REJECTED);
        } catch (RuntimeException ex) {
            taskStore.finish(taskId, LabelshopImportTaskStore.STATUS_FAILED,
                    truncate("发起导入失败：" + ex.getMessage(), 500));
            throw ex;
        }
        return taskId;
    }

    @Override
    public LabelshopImportTaskDO getTask(Long taskId) {
        return taskStore.require(taskId);
    }

    @Override
    public LabelshopImportTaskDO getLatest(Long orgId) {
        return taskMapper.selectLatestByOrg(orgId);
    }

    @Override
    public LabelshopImportTaskDO getLatestForMember(Long memberId) {
        return taskMapper.selectLatestByMember(memberId);
    }

    @Override
    public PageResult<LabelshopImportItemDO> getItems(Long taskId, Integer status, Integer library,
                                                      PageParam pageParam) {
        taskStore.require(taskId);
        return itemMapper.selectPageByTask(taskId, status, library, pageParam);
    }

    @Override
    public LabelshopPrecheck getPrecheck(Long orgId) {
        MaxLabelOrgDO org = orgService.getOrg(orgId);
        if (org == null) {
            throw exception(MEMBER_ORG_NOT_EXISTS);
        }
        MemberUserDO owner = memberUserMapper.selectById(org.getOwnerMemberId());
        LabelshopPrecheck precheck = new LabelshopPrecheck();
        precheck.setOrgId(orgId);
        precheck.setOrgName(org.getName());
        precheck.setOwnerMemberId(org.getOwnerMemberId());
        if (owner != null) {
            precheck.setOwnerNickname(owner.getNickname());
            precheck.setOwnerMobileMasked(maskMobile(owner.getMobile()));
        }
        precheck.setOrgStatus(org.getStatus());
        boolean licenseActive = Objects.equals(org.getStatus(), 0)
                && !licenseService.getUnexpiredList(orgId).isEmpty();
        precheck.setLicenseActive(licenseActive);
        LocalDateTime maxEnd = licenseService.getMaxEndTime(orgId);
        precheck.setLicenseEndDate(maxEnd == null ? null : maxEnd.toLocalDate().minusDays(1));
        precheck.setSeatCount(seatService.getActiveList(orgId).size());
        precheck.setFolderName(LocalDateTime.now().format(FOLDER_TIME) + "导入");
        AppMemberSubscriptionRespVO subscription = owner == null ? null
                : subscriptionService.getCurrent(org.getOwnerMemberId());
        String edition = subscription == null ? null : subscription.getEdition();
        precheck.setPersonalEdition(StrUtil.blankToDefault(edition, "standard"));
        precheck.setPersonalUsedBytes(spaceService.getPersonalUsedBytes(org.getOwnerMemberId()));
        precheck.setPersonalQuotaBytes(spaceService.getPersonalQuotaBytes(org.getOwnerMemberId()));
        precheck.setEnterpriseUsedBytes(spaceService.getEnterpriseUsedBytes(orgId));
        precheck.setEnterpriseQuotaBytes(ENTERPRISE_BYTES);
        LabelshopImportTaskDO running = taskMapper.selectRunningByOrg(orgId);
        precheck.setRunningTaskId(running == null ? null : running.getId());
        return precheck;
    }

    @Override
    public LabelshopPrecheck getMemberPrecheck(Long memberId) {
        MemberUserDO member = memberUserMapper.selectById(memberId);
        if (member == null) {
            throw exception(USER_NOT_EXISTS);
        }
        LabelshopPrecheck precheck = new LabelshopPrecheck();
        precheck.setOwnerMemberId(memberId);
        precheck.setOwnerNickname(member.getNickname());
        precheck.setOwnerMobileMasked(maskMobile(member.getMobile()));
        // 个人任务没有企业维度的门禁：模板层按「启用 + 有效」表示可导入
        precheck.setOrgStatus(0);
        precheck.setLicenseActive(true);
        precheck.setSeatCount(0);
        AppMemberSubscriptionRespVO subscription = subscriptionService.getCurrent(memberId);
        String edition = subscription == null ? null : subscription.getEdition();
        precheck.setPersonalEdition(StrUtil.blankToDefault(edition, "standard"));
        precheck.setPersonalUsedBytes(spaceService.getPersonalUsedBytes(memberId));
        precheck.setPersonalQuotaBytes(spaceService.getPersonalQuotaBytes(memberId));
        precheck.setEnterpriseUsedBytes(0L);
        precheck.setEnterpriseQuotaBytes(0L);
        LabelshopImportTaskDO running = taskMapper.selectRunningByMember(memberId);
        precheck.setRunningTaskId(running == null ? null : running.getId());
        return precheck;
    }

    // ======================= 异步任务 =======================

    private void runTask(Long taskId, String account, String password, List<Long> seatMemberIds) {
        LabelshopImportTaskDO task = taskStore.require(taskId);
        boolean memberTask = Objects.equals(task.getSubjectType(), LabelshopImportTaskDO.SUBJECT_MEMBER);
        int total = 0;
        int processed = 0;
        int success = 0;
        int skip = 0;
        int fail = 0;
        long personalBytes = 0L;
        long enterpriseBytes = 0L;
        List<String> sourceErrors = new ArrayList<>();
        List<String> imageWarnings = new ArrayList<>();
        Map<String, LabelshopImageImporter.ImageData> imageCache = new HashMap<>();
        LabelshopSession session;
        boolean reloginUsed = false;
        try {
            taskStore.markRunning(taskId, PHASE_LOGIN);
            session = client.login(account, password);
            taskStore.updatePhase(taskId, PHASE_LIST);

            LabelshopEnumerationResult enumeration;
            try {
                enumeration = enumerate(session, task, memberTask);
            } catch (LabelshopSessionExpiredException ex) {
                if (reloginUsed) {
                    throw ex;
                }
                session = client.login(account, password);
                reloginUsed = true;
                enumeration = enumerate(session, task, memberTask);
            }
            sourceErrors.addAll(enumeration.getErrors());
            total = enumeration.getItems().size();

            List<PendingItem> pending = new ArrayList<>(total);
            for (LabelshopSourceItem source : enumeration.getItems()) {
                LabelshopImportItemDO item = LabelshopImportItemDO.builder()
                        .taskId(taskId)
                        .sourceLibrary(source.getSourceLibrary())
                        .sourceRef(source.getSourceRef())
                        .sourceCategory(StrUtil.nullToEmpty(source.getSourceCategory()))
                        .sourceName(StrUtil.nullToEmpty(source.getSourceName()))
                        .targetScope(source.getTargetScope())
                        .targetOwnerId(source.getTargetOwnerId())
                        .format(source.getFormat())
                        .editable("lsdx".equals(source.getFormat()))
                        .sizeBytes(0L)
                        .status(0)
                        .build();
                itemMapper.insert(item);
                pending.add(new PendingItem(item, source));
            }
            taskStore.updateCounters(taskId, total, 0, 0, 0, 0, 0, 0, PHASE_IMPORT);

            MaxLabelShareFolderDO folder = memberTask ? null : createFolder(task, seatMemberIds);
            Long folderId = folder == null ? null : folder.getId();

            List<String> personalRefs = enumeration.getItems().stream()
                    .filter(item -> Objects.equals(item.getTargetScope(), SCOPE_PERSONAL))
                    .map(LabelshopSourceItem::getSourceRef).toList();
            List<String> enterpriseRefs = enumeration.getItems().stream()
                    .filter(item -> Objects.equals(item.getTargetScope(), SCOPE_ENTERPRISE))
                    .map(LabelshopSourceItem::getSourceRef).toList();
            Map<String, MaxLabelShareContentDO> personalExisting = existingContent(
                    SCOPE_PERSONAL, task.getOwnerMemberId(), personalRefs);
            Map<String, MaxLabelShareContentDO> enterpriseExisting = existingContent(
                    SCOPE_ENTERPRISE, task.getOrgId(), enterpriseRefs);

            String abortReason = null;
            for (PendingItem pendingItem : pending) {
                if (memberTask) {
                    if (memberUserMapper.selectById(task.getOwnerMemberId()) == null) {
                        abortReason = "会员已被删除";
                        break;
                    }
                } else if (!isOrgWritable(task.getOrgId())) {
                    abortReason = "企业已停用或授权已到期";
                    break;
                }
                LabelshopImportItemDO item = pendingItem.item();
                LabelshopSourceItem source = pendingItem.source();
                MaxLabelShareContentDO existing = Objects.equals(source.getTargetScope(), SCOPE_PERSONAL)
                        ? personalExisting.get(source.getSourceRef())
                        : enterpriseExisting.get(source.getSourceRef());
                if (existing != null) {
                    markSkipped(item, source, existing);
                    skip++;
                    processed++;
                } else {
                    try {
                        long size = importOne(session, enumeration, item, source,
                                task.getOwnerMemberId(), task.getOrgId(), folderId, imageCache, imageWarnings);
                        success++;
                        if (Objects.equals(source.getTargetScope(), SCOPE_PERSONAL)) {
                            personalBytes += size;
                        } else {
                            enterpriseBytes += size;
                        }
                    } catch (LabelshopSessionExpiredException ex) {
                        if (reloginUsed) {
                            abortReason = "历史系统会话失效，重登后仍未恢复";
                            break;
                        }
                        session = client.login(account, password);
                        reloginUsed = true;
                        try {
                            long size = importOne(session, enumeration, item, source,
                                    task.getOwnerMemberId(), task.getOrgId(), folderId, imageCache, imageWarnings);
                            success++;
                            if (Objects.equals(source.getTargetScope(), SCOPE_PERSONAL)) {
                                personalBytes += size;
                            } else {
                                enterpriseBytes += size;
                            }
                        } catch (LabelshopSessionExpiredException retryEx) {
                            abortReason = "历史系统会话失效，重登后仍未恢复";
                            break;
                        } catch (RuntimeException retryEx) {
                            markFailed(item, retryEx.getMessage());
                            fail++;
                        }
                    } catch (LabelshopTemplateException | LabelshopUpstreamException ex) {
                        markFailed(item, ex.getMessage());
                        fail++;
                    } catch (RuntimeException ex) {
                        markFailed(item, "写入失败：" + ex.getMessage());
                        fail++;
                    }
                    processed++;
                }
                if (processed % Math.max(1, labelshopProperties.getBatchSize()) == 0) {
                    taskStore.updateCounters(taskId, total, processed, success, skip, fail,
                            personalBytes, enterpriseBytes, PHASE_IMPORT);
                    refreshUsed(task);
                }
            }

            if (abortReason != null) {
                // Q141 / FR-493：中止时未处理的条目也要出现在失败清单里
                int interrupted = markPendingAsFailed(pending, processed, abortReason);
                fail += interrupted;
                processed += interrupted;
            }
            taskStore.updateCounters(taskId, total, processed, success, skip, fail,
                    personalBytes, enterpriseBytes, PHASE_FINISH);
            refreshUsed(task);
            List<String> finishErrors = new ArrayList<>(sourceErrors);
            finishErrors.addAll(imageWarnings);
            if (abortReason != null) {
                taskStore.finish(taskId, LabelshopImportTaskStore.STATUS_FAILED,
                        joinErrors(abortReason, finishErrors));
            } else if (total == 0 && !sourceErrors.isEmpty()) {
                taskStore.finish(taskId, LabelshopImportTaskStore.STATUS_FAILED,
                        joinErrors(null, finishErrors));
            } else {
                taskStore.finish(taskId, LabelshopImportTaskStore.STATUS_DONE,
                        finishErrors.isEmpty() ? null : joinErrors(null, finishErrors));
            }
        } catch (LabelshopLoginException ex) {
            taskStore.finish(taskId, LabelshopImportTaskStore.STATUS_FAILED, "历史系统登录失败，请检查账号密码");
        } catch (LabelshopSessionExpiredException ex) {
            taskStore.finish(taskId, LabelshopImportTaskStore.STATUS_FAILED,
                    "历史系统会话失效，重登后仍未恢复");
        } catch (Exception ex) {
            taskStore.finish(taskId, LabelshopImportTaskStore.STATUS_FAILED,
                    truncate("导入失败：" + ex.getMessage(), 500));
        }
    }

    private LabelshopEnumerationResult enumerate(LabelshopSession session, LabelshopImportTaskDO task,
                                                 boolean memberTask) {
        return memberTask
                ? enumerator.enumeratePersonal(session, task.getOwnerMemberId())
                : enumerator.enumerate(session, task.getOrgId(), task.getOwnerMemberId());
    }

    private long importOne(LabelshopSession session, LabelshopEnumerationResult enumeration,
                           LabelshopImportItemDO item, LabelshopSourceItem source,
                           Long ownerMemberId, Long orgId, Long folderId,
                           Map<String, LabelshopImageImporter.ImageData> imageCache,
                           List<String> imageWarnings) {
        String content;
        if ("cloud-json".equals(source.getFormat())) {
            LabelshopSts sts = enumeration.getSts();
            if (sts == null) {
                throw new LabelshopTemplateException("云模板 STS 未就绪");
            }
            byte[] bytes = client.getOssObject(sts,
                    StrUtil.blankToDefault(sts.getBucketTmpl(), "cvstmpl"),
                    "lp/" + source.getSourceRef() + ".json");
            content = new String(bytes, StandardCharsets.UTF_8);
        } else {
            int ownType = source.getOwnType() == null ? source.getSourceLibrary() : source.getOwnType();
            content = client.getTemplate(session, source.getSourceRef(), ownType);
        }
        String format = source.getFormat();
        // 个人空间的云端模板（Own_Type=0）转成 .lsdx，转换失败按模板级失败处理
        if (Objects.equals(source.getSourceLibrary(), 12) && Objects.equals(source.getOwnType(), 0)) {
            content = cloudConverter.convert(content);
            format = "lsdx";
        }
        Long targetFolderId = Objects.equals(source.getTargetScope(), SCOPE_ENTERPRISE)
                ? folderId
                : folderService.resolvePersonalFolder(ownerMemberId, source.getSourceCategory());
        // .lsdx 里的 LabelShop 外链图片：写入图片空间并内嵌进模板；图片失败只记警告，不阻断模板
        if ("lsdx".equals(format)) {
            LabelshopImageImporter.Result imageResult = imageImporter.rewrite(content,
                    new LabelshopImageImporter.Target(
                            source.getTargetScope() == null ? SCOPE_PERSONAL : source.getTargetScope(),
                            ownerMemberId,
                            Objects.equals(source.getTargetScope(), SCOPE_ENTERPRISE) ? orgId : null,
                            targetFolderId, source.getSourceCategory()),
                    imageCache);
            content = imageResult.content();
            imageWarnings.addAll(imageResult.warnings());
        }
        int size = content.getBytes(StandardCharsets.UTF_8).length;
        if (size > shareProperties.getContentMaxBytes()) {
            throw new LabelshopTemplateException("超过 16 MB");
        }
        contentWriter.write(item, source, content, ownerMemberId, orgId, targetFolderId, format);
        return size;
    }

    private MaxLabelShareFolderDO createFolder(LabelshopImportTaskDO task, List<Long> seatMemberIds) {
        String planned = task.getFolderName();
        Set<String> names = folderMapper.selectListByOrgId(task.getOrgId()).stream()
                .map(MaxLabelShareFolderDO::getName).collect(Collectors.toSet());
        String name = planned;
        int suffix = 2;
        while (names.contains(name)) {
            name = planned + "_" + suffix++;
        }
        MaxLabelShareFolderDO folder = MaxLabelShareFolderDO.builder()
                .orgId(task.getOrgId())
                .name(name)
                .creatorMemberId(task.getOwnerMemberId())
                .build();
        folderMapper.insert(folder);
        for (Long memberId : seatMemberIds) {
            folderMemberMapper.insert(MaxLabelShareFolderMemberDO.builder()
                    .folderId(folder.getId()).memberId(memberId).build());
        }
        taskStore.setFolder(task.getId(), folder.getId(), name);
        return folder;
    }

    private Map<String, MaxLabelShareContentDO> existingContent(int scope, Long ownerId,
                                                                List<String> refs) {
        Map<String, MaxLabelShareContentDO> result = new HashMap<>();
        if (refs.isEmpty()) {
            return result;
        }
        for (LabelshopImportItemDO item : itemMapper.selectExistingSuccess(scope, ownerId, refs)) {
            if (item.getContentId() == null || result.containsKey(item.getSourceRef())) {
                continue;
            }
            MaxLabelShareContentDO content = contentMapper.selectById(item.getContentId());
            if (content != null) {
                result.put(item.getSourceRef(), content);
            }
        }
        return result;
    }

    private void markSkipped(LabelshopImportItemDO item, LabelshopSourceItem source,
                             MaxLabelShareContentDO content) {
        item.setContentId(content.getId());
        item.setStatus(2);
        item.setFormat(source.getFormat());
        item.setEditable("lsdx".equals(source.getFormat()));
        item.setSizeBytes(content.getSizeBytes() == null ? 0L : content.getSizeBytes());
        itemMapper.updateById(item);
    }

    private int markPendingAsFailed(List<PendingItem> pending, int attempted, String reason) {
        int count = 0;
        for (int i = attempted; i < pending.size(); i++) {
            markFailed(pending.get(i).item(), reason + "（未处理）");
            count++;
        }
        return count;
    }

    private void markFailed(LabelshopImportItemDO item, String message) {
        item.setStatus(3);
        item.setErrorMessage(truncate(StrUtil.blankToDefault(message, "导入失败"), 500));
        itemMapper.updateById(item);
    }

    private boolean isOrgWritable(Long orgId) {
        MaxLabelOrgDO org = orgService.getOrg(orgId);
        return org != null && Objects.equals(org.getStatus(), 0)
                && !licenseService.getUnexpiredList(orgId).isEmpty();
    }

    private void refreshUsed(LabelshopImportTaskDO task) {
        spaceService.refreshUsedBytes(SCOPE_PERSONAL, task.getOwnerMemberId());
        if (task.getOrgId() != null) {
            spaceService.refreshUsedBytes(SCOPE_ENTERPRISE, task.getOrgId());
        }
    }

    private static String joinErrors(String prefix, List<String> errors) {
        String joined = errors.stream().filter(StrUtil::isNotBlank).distinct()
                .collect(Collectors.joining("；"));
        String text = StrUtil.isBlank(prefix) ? joined
                : (StrUtil.isBlank(joined) ? prefix : prefix + "；" + joined);
        return truncate(text, 500);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String maskAccount(String account) {
        if (StrUtil.isBlank(account)) {
            return "";
        }
        int at = account.indexOf('@');
        if (at > 0) {
            return account.substring(0, Math.min(2, at)) + "***" + account.substring(at);
        }
        return account.length() <= 3 ? "***"
                : account.substring(0, 2) + "***" + account.substring(account.length() - 1);
    }

    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private record PendingItem(LabelshopImportItemDO item, LabelshopSourceItem source) {
    }

}
