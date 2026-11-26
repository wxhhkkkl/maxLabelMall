package cn.iocoder.yudao.module.member.service.labelshop;

import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportItemDO;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportTaskDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgLicenseDO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LabelshopImportRunTaskTest {

    private static final Long ORG_ID = 88L;
    private static final Long OWNER_ID = 1001L;
    private static final Long TASK_ID = 9L;
    private static final Long FOLDER_ID = 500L;

    @Mock private MaxLabelOrgService orgService;
    @Mock private MaxLabelOrgSeatService seatService;
    @Mock private MaxLabelOrgLicenseService licenseService;
    @Mock private MemberUserMapper memberUserMapper;
    @Mock private MemberSubscriptionService subscriptionService;
    @Mock private MaxLabelShareFolderMapper folderMapper;
    @Mock private MaxLabelShareFolderMemberMapper folderMemberMapper;
    @Mock private MaxLabelShareContentMapper contentMapper;
    @Mock private LabelshopImportTaskMapper taskMapper;
    @Mock private LabelshopImportItemMapper itemMapper;
    @Mock private LabelshopImportTaskStore taskStore;
    @Mock private LabelshopClient client;
    @Mock private LabelshopSourceEnumerator enumerator;
    @Mock private LabelshopImportContentWriter contentWriter;
    @Mock private LabelshopImageImporter imageImporter;
    @Mock private ShareFolderService folderService;
    @Mock private CloudJsonToLsdxConverter cloudConverter;
    @Mock private ShareSpaceService spaceService;
    @Mock private ShareProperties shareProperties;
    @Mock private LabelshopImportProperties labelshopProperties;
    @Mock private ThreadPoolTaskExecutor executor;

    @InjectMocks private LabelshopImportServiceImpl service;

    private LabelshopImportTaskDO task;

    @BeforeEach
    void setUp() {
        task = LabelshopImportTaskDO.builder()
                .id(TASK_ID).orgId(ORG_ID).ownerMemberId(OWNER_ID)
                .folderName("20261112173000导入").sourceAccountMasked("us***@example.com")
                .status(0).runningOrgId(ORG_ID).build();
        lenient().when(taskStore.create(any())).thenAnswer(invocation -> {
            LabelshopImportTaskDO created = invocation.getArgument(0);
            created.setId(TASK_ID);
            return created;
        });
        lenient().when(taskStore.require(TASK_ID)).thenReturn(task);
        lenient().when(taskMapper.selectRunningByOrg(ORG_ID)).thenReturn(null);
        lenient().when(seatService.getActiveList(ORG_ID)).thenReturn(List.of(seat(OWNER_ID)));
        lenient().when(memberUserMapper.selectById(OWNER_ID)).thenReturn(member());
        lenient().when(licenseService.getUnexpiredList(ORG_ID))
                .thenReturn(List.of(new MaxLabelOrgLicenseDO()));
        lenient().when(orgService.getOrg(ORG_ID)).thenReturn(org(0));
        lenient().when(shareProperties.getContentMaxBytes()).thenReturn(16L * 1024 * 1024);
        lenient().when(labelshopProperties.getBatchSize()).thenReturn(20);
        lenient().when(imageImporter.rewrite(anyString(), any(), any())).thenAnswer(invocation ->
                new LabelshopImageImporter.Result(invocation.getArgument(0), List.of()));
        lenient().doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        lenient().when(folderMapper.selectListByOrgId(ORG_ID)).thenReturn(List.of());
        lenient().doAnswer(invocation -> {
            ((MaxLabelShareFolderDO) invocation.getArgument(0)).setId(FOLDER_ID);
            return 1;
        }).when(folderMapper).insert(any(MaxLabelShareFolderDO.class));
        lenient().doAnswer(invocation -> {
            LabelshopImportItemDO item = invocation.getArgument(0);
            if (item.getId() == null) {
                item.setId(100L + Math.abs(item.getSourceRef().hashCode() % 1000));
            }
            return 1;
        }).when(itemMapper).insert(any(LabelshopImportItemDO.class));
        lenient().when(itemMapper.selectExistingSuccess(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void runTask_importsPersonalAndEnterpriseAndCreatesFolderAcl() {
        when(client.login("user", "pwd")).thenReturn(session());
        LabelshopSts sts = new LabelshopSts();
        sts.setBucketTmpl("cvstmpl");
        LabelshopEnumerationResult result = result(
                item(12, "p1", "我的模板库/上衣", "P1", "lsdx", 1, OWNER_ID),
                item(1, "e1", "企业/组织模板/云马", "E1", "cloud-json", 2, ORG_ID));
        result.setSts(sts);
        when(enumerator.enumerate(any(), eq(ORG_ID), eq(OWNER_ID))).thenReturn(result);
        when(client.getTemplate(any(), eq("p1"), eq(12))).thenReturn("<xml/>");
        when(client.getOssObject(eq(sts), eq("cvstmpl"), eq("lp/e1.json")))
                .thenReturn("{\"docid\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        when(folderService.resolvePersonalFolder(OWNER_ID, "我的模板库/上衣")).thenReturn(600L);
        when(contentWriter.write(any(), any(), any(), eq(OWNER_ID), eq(ORG_ID), eq(600L), eq("lsdx")))
                .thenReturn(1L);
        when(contentWriter.write(any(), any(), any(), eq(OWNER_ID), eq(ORG_ID), eq(FOLDER_ID), eq("cloud-json")))
                .thenReturn(1L);

        Long taskId = service.start(ORG_ID, "user", "pwd", true, 1L, "op");

        assertEquals(TASK_ID, taskId);
        verify(contentWriter).write(any(), any(), any(),
                eq(OWNER_ID), eq(ORG_ID), eq(600L), eq("lsdx"));
        verify(contentWriter).write(any(), any(), any(),
                eq(OWNER_ID), eq(ORG_ID), eq(FOLDER_ID), eq("cloud-json"));
        verify(folderMapper).insert(any(MaxLabelShareFolderDO.class));
        verify(folderMapper, times(1)).selectListByOrgId(ORG_ID);
        verify(seatService, times(1)).getActiveList(ORG_ID);
        verify(folderMemberMapper, times(1)).insert(any(MaxLabelShareFolderMemberDO.class));
        verify(taskStore).finish(eq(TASK_ID), eq(LabelshopImportTaskStore.STATUS_DONE), any());
        verify(taskStore).updateCounters(eq(TASK_ID), eq(2), eq(2), eq(2), eq(0), eq(0),
                anyLong(), anyLong(), eq("FINISH"));
        verify(spaceService, atLeastOnce()).refreshUsedBytes(eq(1), eq(OWNER_ID));
        verify(spaceService, atLeastOnce()).refreshUsedBytes(eq(2), eq(ORG_ID));
    }

    @Test
    void runTask_loginFailureDoesNotCreateFolderOrItems() {
        when(client.login("user", "pwd")).thenThrow(new LabelshopLoginException("bad"));

        service.start(ORG_ID, "user", "pwd", true, 1L, "op");

        verify(folderMapper, never()).insert(any(MaxLabelShareFolderDO.class));
        verify(folderMemberMapper, never()).insert(any(MaxLabelShareFolderMemberDO.class));
        verify(itemMapper, never()).insert(any(LabelshopImportItemDO.class));
        verify(taskStore).finish(eq(TASK_ID), eq(LabelshopImportTaskStore.STATUS_FAILED),
                argThat(message -> message != null && message.contains("登录失败")));
    }

    @Test
    void runTask_skipsExistingLiveContent() {
        when(client.login("user", "pwd")).thenReturn(session());
        LabelshopEnumerationResult result = result(
                item(12, "p1", "我的模板库", "P1", "lsdx", 1, OWNER_ID));
        when(enumerator.enumerate(any(), eq(ORG_ID), eq(OWNER_ID))).thenReturn(result);
        LabelshopImportItemDO existing = LabelshopImportItemDO.builder()
                .id(77L).sourceRef("p1").contentId(55L).build();
        when(itemMapper.selectExistingSuccess(eq(1), eq(OWNER_ID), any()))
                .thenReturn(List.of(existing));
        when(contentMapper.selectById(55L))
                .thenReturn(MaxLabelShareContentDO.builder().id(55L).sizeBytes(123L).build());

        service.start(ORG_ID, "user", "pwd", true, 1L, "op");

        verifyNoInteractions(contentWriter);
        ArgumentCaptor<LabelshopImportItemDO> skipCaptor =
                ArgumentCaptor.forClass(LabelshopImportItemDO.class);
        verify(itemMapper).updateById(skipCaptor.capture());
        assertEquals(2, skipCaptor.getValue().getStatus());
        assertEquals(55L, skipCaptor.getValue().getContentId());
        verify(taskStore).finish(eq(TASK_ID), eq(LabelshopImportTaskStore.STATUS_DONE), any());
        verify(taskStore).updateCounters(eq(TASK_ID), eq(1), eq(1), eq(0), eq(1), eq(0),
                eq(0L), eq(0L), eq("FINISH"));
    }

    @Test
    void runTask_marksOversizeItemFailedWithoutWriting() {
        when(shareProperties.getContentMaxBytes()).thenReturn(10L);
        when(client.login("user", "pwd")).thenReturn(session());
        LabelshopEnumerationResult result = result(
                item(12, "p1", "我的模板库", "P1", "lsdx", 1, OWNER_ID));
        when(enumerator.enumerate(any(), eq(ORG_ID), eq(OWNER_ID))).thenReturn(result);
        when(client.getTemplate(any(), eq("p1"), eq(12))).thenReturn("01234567890123456789");

        service.start(ORG_ID, "user", "pwd", true, 1L, "op");

        verifyNoInteractions(contentWriter);
        verify(client, times(1)).login("user", "pwd");
        ArgumentCaptor<LabelshopImportItemDO> failCaptor =
                ArgumentCaptor.forClass(LabelshopImportItemDO.class);
        verify(itemMapper).updateById(failCaptor.capture());
        assertEquals(3, failCaptor.getValue().getStatus());
        assertEquals("超过 16 MB", failCaptor.getValue().getErrorMessage());
        verify(taskStore).finish(eq(TASK_ID), eq(LabelshopImportTaskStore.STATUS_DONE), any());
        verify(taskStore).updateCounters(eq(TASK_ID), eq(1), eq(1), eq(0), eq(0), eq(1),
                eq(0L), eq(0L), eq("FINISH"));
    }

    @Test
    void runTask_stopsNewWritesWhenOrgDisabledMidway() {
        when(client.login("user", "pwd")).thenReturn(session());
        LabelshopEnumerationResult result = result(
                item(12, "p1", "我的模板库", "P1", "lsdx", 1, OWNER_ID),
                item(12, "p2", "我的模板库", "P2", "lsdx", 1, OWNER_ID));
        when(enumerator.enumerate(any(), eq(ORG_ID), eq(OWNER_ID))).thenReturn(result);
        when(client.getTemplate(any(), eq("p1"), eq(12))).thenReturn("<xml/>");
        when(orgService.getOrg(ORG_ID)).thenReturn(org(0), org(0), org(1));
        when(folderService.resolvePersonalFolder(OWNER_ID, "我的模板库")).thenReturn(600L);

        service.start(ORG_ID, "user", "pwd", true, 1L, "op");

        verify(contentWriter, times(1)).write(any(), any(), any(),
                eq(OWNER_ID), eq(ORG_ID), eq(600L), eq("lsdx"));
        verify(taskStore).finish(eq(TASK_ID), eq(LabelshopImportTaskStore.STATUS_FAILED),
                argThat(message -> message != null && message.contains("企业已停用")));
        verify(taskStore).updateCounters(eq(TASK_ID), eq(2), eq(2), eq(1), eq(0), eq(1),
                anyLong(), eq(0L), eq("FINISH"));
        ArgumentCaptor<LabelshopImportItemDO> interruptedCaptor =
                ArgumentCaptor.forClass(LabelshopImportItemDO.class);
        verify(itemMapper).updateById(interruptedCaptor.capture());
        assertEquals(3, interruptedCaptor.getValue().getStatus());
        assertEquals("p2", interruptedCaptor.getValue().getSourceRef());
        assertEquals("企业已停用或授权已到期（未处理）",
                interruptedCaptor.getValue().getErrorMessage());
    }

    @Test
    void runTask_reloginsOnceOnSessionExpiry() {
        LabelshopSession first = session();
        LabelshopSession second = session();
        when(client.login("user", "pwd")).thenReturn(first, second);
        LabelshopEnumerationResult result = result(
                item(12, "p1", "我的模板库", "P1", "lsdx", 1, OWNER_ID));
        when(enumerator.enumerate(any(), eq(ORG_ID), eq(OWNER_ID))).thenReturn(result);
        when(client.getTemplate(any(), eq("p1"), eq(12)))
                .thenThrow(new LabelshopSessionExpiredException("expired"))
                .thenReturn("<xml/>");
        when(folderService.resolvePersonalFolder(OWNER_ID, "我的模板库")).thenReturn(600L);
        when(contentWriter.write(any(), any(), any(), eq(OWNER_ID), eq(ORG_ID), eq(600L), eq("lsdx")))
                .thenReturn(1L);

        service.start(ORG_ID, "user", "pwd", true, 1L, "op");

        verify(client, times(2)).login("user", "pwd");
        verify(contentWriter, times(1)).write(any(), any(), any(),
                eq(OWNER_ID), eq(ORG_ID), eq(600L), eq("lsdx"));
        verify(taskStore).finish(eq(TASK_ID), eq(LabelshopImportTaskStore.STATUS_DONE), any());
    }

    @Test
    void runTask_abortsWhenReloginStillExpired() {
        when(client.login("user", "pwd")).thenReturn(session(), session());
        LabelshopEnumerationResult result = result(
                item(12, "p1", "我的模板库", "P1", "lsdx", 1, OWNER_ID));
        when(enumerator.enumerate(any(), eq(ORG_ID), eq(OWNER_ID))).thenReturn(result);
        when(client.getTemplate(any(), eq("p1"), eq(12)))
                .thenThrow(new LabelshopSessionExpiredException("expired"));

        service.start(ORG_ID, "user", "pwd", true, 1L, "op");

        verify(client, times(2)).login("user", "pwd");
        verifyNoInteractions(contentWriter);
        verify(taskStore).finish(eq(TASK_ID), eq(LabelshopImportTaskStore.STATUS_FAILED),
                argThat(message -> message != null && message.contains("会话失效")));
        ArgumentCaptor<LabelshopImportItemDO> interruptedCaptor =
                ArgumentCaptor.forClass(LabelshopImportItemDO.class);
        verify(itemMapper).updateById(interruptedCaptor.capture());
        assertEquals(3, interruptedCaptor.getValue().getStatus());
        assertTrue(interruptedCaptor.getValue().getErrorMessage().contains("未处理"));
    }

    @Test
    void runTask_recordsSourceLevelFailureWhenAllSourcesFail() {
        when(client.login("user", "pwd")).thenReturn(session());
        LabelshopEnumerationResult result = new LabelshopEnumerationResult();
        result.getErrors().add("我的模板库分类树读取失败：x");
        when(enumerator.enumerate(any(), eq(ORG_ID), eq(OWNER_ID))).thenReturn(result);

        service.start(ORG_ID, "user", "pwd", true, 1L, "op");

        verifyNoInteractions(contentWriter);
        verify(taskStore).finish(eq(TASK_ID), eq(LabelshopImportTaskStore.STATUS_FAILED),
                argThat(message -> message != null && message.contains("我的模板库")));
    }

    @Test
    void runTask_importsIntoMemberPersonalSpaceWithCategoryFolder() {
        when(taskStore.require(TASK_ID)).thenReturn(memberTask());
        when(client.login("user", "pwd")).thenReturn(session());
        LabelshopEnumerationResult result = result(
                item(12, "p1", "我的模板库", "P1", "lsdx", 1, OWNER_ID));
        when(enumerator.enumeratePersonal(any(), eq(OWNER_ID))).thenReturn(result);
        when(client.getTemplate(any(), eq("p1"), eq(12))).thenReturn("<xml/>");
        when(folderService.resolvePersonalFolder(OWNER_ID, "我的模板库")).thenReturn(600L);
        when(contentWriter.write(any(), any(), any(), eq(OWNER_ID), isNull(), eq(600L), eq("lsdx")))
                .thenReturn(12L);

        service.startForMember(OWNER_ID, "user", "pwd", true, 1L, "op");

        verify(folderMapper, never()).insert(any(MaxLabelShareFolderDO.class));
        verify(contentWriter, times(1)).write(any(), any(), any(),
                eq(OWNER_ID), isNull(), eq(600L), eq("lsdx"));
        verify(spaceService).refreshUsedBytes(eq(1), eq(OWNER_ID));
        verify(spaceService, never()).refreshUsedBytes(eq(2), any());
        verify(taskStore).updateCounters(eq(TASK_ID), eq(1), eq(1), eq(1), eq(0), eq(0),
                eq(6L), eq(0L), eq("FINISH"));
        verify(taskStore).finish(eq(TASK_ID), eq(LabelshopImportTaskStore.STATUS_DONE), any());
    }

    @Test
    void runTask_convertsPersonalCloudJsonToLsdx() {
        when(client.login("user", "pwd")).thenReturn(session());
        LabelshopSts sts = new LabelshopSts();
        sts.setBucketTmpl("cvstmpl");
        LabelshopSourceItem cloud = LabelshopSourceItem.builder()
                .sourceLibrary(12).sourceRef("c1").sourceCategory("我的模板")
                .sourceName("C1").format("cloud-json").ownType(0)
                .targetScope(1).targetOwnerId(OWNER_ID).build();
        LabelshopEnumerationResult result = result(cloud);
        result.setSts(sts);
        when(enumerator.enumerate(any(), eq(ORG_ID), eq(OWNER_ID))).thenReturn(result);
        when(client.getOssObject(eq(sts), eq("cvstmpl"), eq("lp/c1.json")))
                .thenReturn("{\"lfrm\":{}}".getBytes(StandardCharsets.UTF_8));
        when(cloudConverter.convert(anyString())).thenReturn("<labelshopdocument/>");
        when(folderService.resolvePersonalFolder(OWNER_ID, "我的模板")).thenReturn(600L);
        when(contentWriter.write(any(), any(), any(), eq(OWNER_ID), eq(ORG_ID), eq(600L), eq("lsdx")))
                .thenReturn(1L);

        service.start(ORG_ID, "user", "pwd", true, 1L, "op");

        verify(cloudConverter).convert("{\"lfrm\":{}}");
        verify(contentWriter).write(any(), any(), any(),
                eq(OWNER_ID), eq(ORG_ID), eq(600L), eq("lsdx"));
        verify(taskStore).finish(eq(TASK_ID), eq(LabelshopImportTaskStore.STATUS_DONE), any());
    }

    @Test
    void runTask_surfacesImageWarningsWithoutFailingTemplate() {
        when(client.login("user", "pwd")).thenReturn(session());
        LabelshopEnumerationResult result = result(
                item(12, "p1", "我的模板库", "P1", "lsdx", 1, OWNER_ID));
        when(enumerator.enumerate(any(), eq(ORG_ID), eq(OWNER_ID))).thenReturn(result);
        when(client.getTemplate(any(), eq("p1"), eq(12))).thenReturn("<xml/>");
        when(imageImporter.rewrite(anyString(), any(), any())).thenReturn(
                new LabelshopImageImporter.Result("<xml/>", List.of("图片 logo.png 导入失败：HTTP 404")));
        when(folderService.resolvePersonalFolder(OWNER_ID, "我的模板库")).thenReturn(600L);
        when(contentWriter.write(any(), any(), any(), eq(OWNER_ID), eq(ORG_ID), eq(600L), eq("lsdx")))
                .thenReturn(1L);

        service.start(ORG_ID, "user", "pwd", true, 1L, "op");

        verify(contentWriter, times(1)).write(any(), any(), any(),
                eq(OWNER_ID), eq(ORG_ID), eq(600L), eq("lsdx"));
        verify(taskStore).finish(eq(TASK_ID), eq(LabelshopImportTaskStore.STATUS_DONE),
                argThat(message -> message != null && message.contains("logo.png")));
        verify(taskStore).updateCounters(eq(TASK_ID), eq(1), eq(1), eq(1), eq(0), eq(0),
                anyLong(), anyLong(), eq("FINISH"));
    }

    @Test
    void runTask_marksItemFailedWhenCloudConversionFails() {
        when(client.login("user", "pwd")).thenReturn(session());
        LabelshopSts sts = new LabelshopSts();
        sts.setBucketTmpl("cvstmpl");
        LabelshopSourceItem cloud = LabelshopSourceItem.builder()
                .sourceLibrary(12).sourceRef("c1").sourceCategory("我的模板")
                .sourceName("C1").format("cloud-json").ownType(0)
                .targetScope(1).targetOwnerId(OWNER_ID).build();
        LabelshopEnumerationResult result = result(cloud);
        result.setSts(sts);
        when(enumerator.enumerate(any(), eq(ORG_ID), eq(OWNER_ID))).thenReturn(result);
        when(client.getOssObject(eq(sts), eq("cvstmpl"), eq("lp/c1.json")))
                .thenReturn("{\"lfrm\":{}}".getBytes(StandardCharsets.UTF_8));
        when(cloudConverter.convert(anyString())).thenThrow(new LabelshopTemplateException("不是云端模板"));

        service.start(ORG_ID, "user", "pwd", true, 1L, "op");

        verifyNoInteractions(contentWriter);
        ArgumentCaptor<LabelshopImportItemDO> captor =
                ArgumentCaptor.forClass(LabelshopImportItemDO.class);
        verify(itemMapper).updateById(captor.capture());
        assertEquals(3, captor.getValue().getStatus());
        assertTrue(captor.getValue().getErrorMessage().contains("不是云端模板"));
        verify(taskStore).finish(eq(TASK_ID), eq(LabelshopImportTaskStore.STATUS_DONE), any());
    }

    // ======================= 辅助 =======================

    private static LabelshopImportTaskDO memberTask() {
        return LabelshopImportTaskDO.builder()
                .id(TASK_ID).subjectType(LabelshopImportTaskDO.SUBJECT_MEMBER)
                .ownerMemberId(OWNER_ID).sourceAccountMasked("us***@example.com")
                .status(0).runningMemberId(OWNER_ID).build();
    }

    private static LabelshopEnumerationResult result(LabelshopSourceItem... items) {
        LabelshopEnumerationResult result = new LabelshopEnumerationResult();
        result.setItems(List.of(items));
        return result;
    }

    private static LabelshopSourceItem item(int library, String ref, String category, String name,
                                            String format, int scope, Long targetOwnerId) {
        return LabelshopSourceItem.builder()
                .sourceLibrary(library).sourceRef(ref).sourceCategory(category)
                .sourceName(name).format(format).ownType(library == 1 ? 1 : 12)
                .targetScope(scope).targetOwnerId(targetOwnerId).build();
    }

    private static LabelshopSession session() {
        LabelshopSession session = new LabelshopSession();
        session.setToken("tok");
        session.setUid(26L);
        session.setMid("mid");
        session.setCompanies(List.of());
        return session;
    }

    private static MaxLabelOrgDO org(int status) {
        MaxLabelOrgDO org = new MaxLabelOrgDO();
        org.setId(ORG_ID);
        org.setName("云马科技");
        org.setOwnerMemberId(OWNER_ID);
        org.setStatus(status);
        return org;
    }

    private static MaxLabelOrgSeatDO seat(Long memberId) {
        return MaxLabelOrgSeatDO.builder().orgId(ORG_ID).memberId(memberId).status(0).build();
    }

    private static MemberUserDO member() {
        MemberUserDO member = new MemberUserDO();
        member.setId(OWNER_ID);
        member.setNickname("张三");
        return member;
    }

}
