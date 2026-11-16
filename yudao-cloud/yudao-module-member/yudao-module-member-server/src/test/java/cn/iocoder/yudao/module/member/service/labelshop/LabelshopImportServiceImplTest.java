package cn.iocoder.yudao.module.member.service.labelshop;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.app.subscription.vo.AppMemberSubscriptionRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportItemDO;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportTaskDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgLicenseDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgSeatDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.labelshop.LabelshopImportItemMapper;
import cn.iocoder.yudao.module.member.dal.mysql.labelshop.LabelshopImportTaskMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopPrecheck;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgLicenseService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgSeatService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgService;
import cn.iocoder.yudao.module.member.service.share.ShareSpaceService;
import cn.iocoder.yudao.module.member.service.subscription.MemberSubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LabelshopImportServiceImplTest {

    private static final Long ORG_ID = 88L;
    private static final Long OWNER_ID = 1001L;

    @Mock private MaxLabelOrgService orgService;
    @Mock private MaxLabelOrgSeatService seatService;
    @Mock private MaxLabelOrgLicenseService licenseService;
    @Mock private MemberUserMapper memberUserMapper;
    @Mock private LabelshopImportTaskMapper taskMapper;
    @Mock private LabelshopImportItemMapper itemMapper;
    @Mock private LabelshopImportTaskStore taskStore;
    @Mock private MemberSubscriptionService subscriptionService;
    @Mock private ShareSpaceService spaceService;
    @Mock private ThreadPoolTaskExecutor executor;

    @InjectMocks private LabelshopImportServiceImpl service;

    @Test
    void start_requiresCredentialsAndAuthorization() {
        assertError(MEMBER_LABELSHOP_CREDENTIAL_REQUIRED.getCode(),
                () -> service.start(ORG_ID, "", "", false, 1L, "op"));
    }

    @Test
    void start_rejectsMissingOrg() {
        when(orgService.getOrg(ORG_ID)).thenReturn(null);
        assertError(MEMBER_ORG_NOT_EXISTS.getCode(),
                () -> service.start(ORG_ID, "user", "pwd", true, 1L, "op"));
    }

    @Test
    void start_rejectsDisabledOrg() {
        MaxLabelOrgDO org = org(1);
        when(orgService.getOrg(ORG_ID)).thenReturn(org);
        assertError(MEMBER_ORG_DISABLED.getCode(),
                () -> service.start(ORG_ID, "user", "pwd", true, 1L, "op"));
    }

    @Test
    void start_rejectsMissingOwner() {
        when(orgService.getOrg(ORG_ID)).thenReturn(org(0));
        when(memberUserMapper.selectById(OWNER_ID)).thenReturn(null);
        assertError(MEMBER_LABELSHOP_OWNER_MISSING.getCode(),
                () -> service.start(ORG_ID, "user", "pwd", true, 1L, "op"));
    }

    @Test
    void start_rejectsExpiredLicense() {
        when(orgService.getOrg(ORG_ID)).thenReturn(org(0));
        when(memberUserMapper.selectById(OWNER_ID)).thenReturn(member());
        when(licenseService.getUnexpiredList(ORG_ID)).thenReturn(List.of());
        assertError(MEMBER_LABELSHOP_ORG_READ_ONLY.getCode(),
                () -> service.start(ORG_ID, "user", "pwd", true, 1L, "op"));
    }

    @Test
    void start_rejectsRunningTask() {
        when(orgService.getOrg(ORG_ID)).thenReturn(org(0));
        when(memberUserMapper.selectById(OWNER_ID)).thenReturn(member());
        when(licenseService.getUnexpiredList(ORG_ID)).thenReturn(List.of(new MaxLabelOrgLicenseDO()));
        when(taskMapper.selectRunningByOrg(ORG_ID)).thenReturn(new LabelshopImportTaskDO());
        assertError(MEMBER_LABELSHOP_IMPORT_RUNNING.getCode(),
                () -> service.start(ORG_ID, "user", "pwd", true, 1L, "op"));
    }

    @Test
    void start_createsPendingTaskAndSubmits() {
        when(orgService.getOrg(ORG_ID)).thenReturn(org(0));
        when(memberUserMapper.selectById(OWNER_ID)).thenReturn(member());
        when(licenseService.getUnexpiredList(ORG_ID)).thenReturn(List.of(new MaxLabelOrgLicenseDO()));
        when(taskMapper.selectRunningByOrg(ORG_ID)).thenReturn(null);
        when(seatService.getActiveList(ORG_ID)).thenReturn(List.of());
        when(taskStore.create(any())).thenAnswer(invocation -> {
            LabelshopImportTaskDO task = invocation.getArgument(0);
            task.setId(9L);
            return task;
        });

        Long taskId = service.start(ORG_ID, "abcdef@example.com", "pwd", true, 1L, "运营");

        assertEquals(9L, taskId);
        ArgumentCaptor<LabelshopImportTaskDO> captor =
                ArgumentCaptor.forClass(LabelshopImportTaskDO.class);
        verify(taskStore).create(captor.capture());
        LabelshopImportTaskDO task = captor.getValue();
        assertEquals(LabelshopImportTaskDO.SUBJECT_ORG, task.getSubjectType());
        assertEquals(ORG_ID, task.getOrgId());
        assertEquals(OWNER_ID, task.getOwnerMemberId());
        assertEquals(ORG_ID, task.getRunningOrgId());
        assertEquals("ab***@example.com", task.getSourceAccountMasked());
        assertTrue(task.getFolderName().endsWith("导入"));
    }

    @Test
    void getTaskAndLatest_delegateToStore() {
        LabelshopImportTaskDO task = new LabelshopImportTaskDO();
        task.setId(9L);
        when(taskStore.require(9L)).thenReturn(task);
        when(taskMapper.selectLatestByOrg(ORG_ID)).thenReturn(task);

        assertEquals(9L, service.getTask(9L).getId());
        assertEquals(9L, service.getLatest(ORG_ID).getId());
    }

    @Test
    void getItems_delegatesWithFilters() {
        LabelshopImportTaskDO task = new LabelshopImportTaskDO();
        task.setId(9L);
        PageParam page = new PageParam();
        page.setPageNo(1);
        page.setPageSize(10);
        LabelshopImportItemDO item = new LabelshopImportItemDO();
        item.setId(77L);
        PageResult<LabelshopImportItemDO> expected = new PageResult<>(List.of(item), 1L);
        when(taskStore.require(9L)).thenReturn(task);
        when(itemMapper.selectPageByTask(9L, 3, 12, page)).thenReturn(expected);

        PageResult<LabelshopImportItemDO> result = service.getItems(9L, 3, 12, page);

        assertEquals(expected, result);
    }

    @Test
    void getPrecheck_mapsOwnerLicenseSeatsAndCapacity() {
        when(orgService.getOrg(ORG_ID)).thenReturn(org(0));
        when(memberUserMapper.selectById(OWNER_ID)).thenReturn(member());
        when(licenseService.getUnexpiredList(ORG_ID))
                .thenReturn(List.of(new MaxLabelOrgLicenseDO()));
        when(licenseService.getMaxEndTime(ORG_ID))
                .thenReturn(LocalDateTime.of(2027, 10, 27, 0, 0));
        when(seatService.getActiveList(ORG_ID))
                .thenReturn(List.of(seat(1001L), seat(1002L)));
        AppMemberSubscriptionRespVO subscription = new AppMemberSubscriptionRespVO();
        subscription.setEdition("professional");
        when(subscriptionService.getCurrent(OWNER_ID)).thenReturn(subscription);
        when(spaceService.getPersonalUsedBytes(OWNER_ID)).thenReturn(10L);
        when(spaceService.getPersonalQuotaBytes(OWNER_ID)).thenReturn(100L);
        when(spaceService.getEnterpriseUsedBytes(ORG_ID)).thenReturn(20L);

        LabelshopPrecheck precheck = service.getPrecheck(ORG_ID);

        assertEquals("云马科技", precheck.getOrgName());
        assertEquals(OWNER_ID, precheck.getOwnerMemberId());
        assertEquals("张三", precheck.getOwnerNickname());
        assertTrue(precheck.getLicenseActive());
        assertEquals(LocalDate.of(2027, 10, 26), precheck.getLicenseEndDate());
        assertEquals(2, precheck.getSeatCount());
        assertEquals("professional", precheck.getPersonalEdition());
        assertEquals(10L, precheck.getPersonalUsedBytes());
        assertEquals(100L, precheck.getPersonalQuotaBytes());
        assertEquals(20L, precheck.getEnterpriseUsedBytes());
        assertEquals(314572800L, precheck.getEnterpriseQuotaBytes());
        assertTrue(precheck.getFolderName().endsWith("导入"));
    }

    @Test
    void startForMember_rejectsMissingMember() {
        when(memberUserMapper.selectById(1001L)).thenReturn(null);
        assertError(USER_NOT_EXISTS.getCode(),
                () -> service.startForMember(1001L, "user", "pwd", true, 1L, "op"));
    }

    @Test
    void startForMember_rejectsRunningTask() {
        when(memberUserMapper.selectById(1001L)).thenReturn(member());
        when(taskMapper.selectRunningByMember(1001L)).thenReturn(new LabelshopImportTaskDO());
        assertError(MEMBER_LABELSHOP_IMPORT_RUNNING.getCode(),
                () -> service.startForMember(1001L, "user", "pwd", true, 1L, "op"));
    }

    @Test
    void startForMember_createsMemberTaskWithoutFolder() {
        when(memberUserMapper.selectById(1001L)).thenReturn(member());
        when(taskMapper.selectRunningByMember(1001L)).thenReturn(null);
        when(taskStore.create(any())).thenAnswer(invocation -> {
            LabelshopImportTaskDO task = invocation.getArgument(0);
            task.setId(7L);
            return task;
        });

        Long taskId = service.startForMember(1001L, "abcdef@example.com", "pwd", true, 1L, "运营");

        assertEquals(7L, taskId);
        ArgumentCaptor<LabelshopImportTaskDO> captor =
                ArgumentCaptor.forClass(LabelshopImportTaskDO.class);
        verify(taskStore).create(captor.capture());
        LabelshopImportTaskDO task = captor.getValue();
        assertEquals(LabelshopImportTaskDO.SUBJECT_MEMBER, task.getSubjectType());
        assertEquals(1001L, task.getOwnerMemberId());
        assertEquals(1001L, task.getRunningMemberId());
        assertNull(task.getOrgId());
        assertNull(task.getFolderName());
        assertEquals("ab***@example.com", task.getSourceAccountMasked());
    }

    @Test
    void getMemberPrecheck_mapsMemberAndPersonalCapacity() {
        when(memberUserMapper.selectById(1001L)).thenReturn(member());
        AppMemberSubscriptionRespVO subscription = new AppMemberSubscriptionRespVO();
        subscription.setEdition("standard");
        when(subscriptionService.getCurrent(1001L)).thenReturn(subscription);
        when(spaceService.getPersonalUsedBytes(1001L)).thenReturn(10L);
        when(spaceService.getPersonalQuotaBytes(1001L)).thenReturn(100L);
        when(taskMapper.selectRunningByMember(1001L)).thenReturn(null);

        LabelshopPrecheck precheck = service.getMemberPrecheck(1001L);

        assertEquals(1001L, precheck.getOwnerMemberId());
        assertEquals("张三", precheck.getOwnerNickname());
        assertEquals(0, precheck.getOrgStatus());
        assertTrue(precheck.getLicenseActive());
        assertEquals(0, precheck.getSeatCount());
        assertEquals("standard", precheck.getPersonalEdition());
        assertEquals(10L, precheck.getPersonalUsedBytes());
        assertEquals(100L, precheck.getPersonalQuotaBytes());
        assertEquals(0L, precheck.getEnterpriseUsedBytes());
        assertNull(precheck.getRunningTaskId());
    }

    private static void assertError(int code, Runnable runnable) {
        ServiceException exception = assertThrows(ServiceException.class, runnable::run);
        assertEquals(code, exception.getCode());
    }

    private static MaxLabelOrgDO org(int status) {
        MaxLabelOrgDO org = new MaxLabelOrgDO();
        org.setId(ORG_ID);
        org.setName("云马科技");
        org.setOwnerMemberId(OWNER_ID);
        org.setStatus(status);
        return org;
    }

    private static MemberUserDO member() {
        MemberUserDO member = new MemberUserDO();
        member.setId(OWNER_ID);
        member.setNickname("张三");
        member.setMobile("13800008000");
        return member;
    }

    private static MaxLabelOrgSeatDO seat(Long memberId) {
        return MaxLabelOrgSeatDO.builder().orgId(ORG_ID).memberId(memberId).status(0).build();
    }

}
