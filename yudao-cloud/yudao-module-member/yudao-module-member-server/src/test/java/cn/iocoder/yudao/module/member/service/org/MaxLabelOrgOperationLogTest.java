package cn.iocoder.yudao.module.member.service.org;

import com.mzt.logapi.starter.annotation.LogRecord;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static cn.iocoder.yudao.module.member.enums.LogRecordConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * FR-318：企业创建、停用、角色变更、OWNER 转交、席位分配与撤销，
 * 必须留下「谁在什么时间做了什么」。
 *
 * 这里用反射守住注解本身 —— 真正的落库由 yudao 的 operate-log 框架完成，
 * 单测里没有 AOP 容器，断言注解不丢失即可防住「改代码时顺手删掉」的回归。
 */
public class MaxLabelOrgOperationLogTest {

    private LogRecord logRecordOf(Class<?> clazz, String method, Class<?>... params) throws Exception {
        Method target = clazz.getMethod(method, params);
        return target.getAnnotation(LogRecord.class);
    }

    @Test
    public void createOrgIsLogged() throws Exception {
        LogRecord log = logRecordOf(MaxLabelOrgServiceImpl.class,
                "createOrg", String.class, Long.class, String.class);
        assertNotNull(log, "企业创建缺少 @LogRecord");
        assertEquals(MEMBER_ORG_TYPE, log.type());
        assertEquals(MEMBER_ORG_CREATE_SUB_TYPE, log.subType());
    }

    @Test
    public void updateStatusIsLogged() throws Exception {
        LogRecord log = logRecordOf(MaxLabelOrgServiceImpl.class,
                "updateStatus", Long.class, Integer.class);
        assertNotNull(log, "企业停用/启用缺少 @LogRecord");
        assertEquals(MEMBER_ORG_UPDATE_STATUS_SUB_TYPE, log.subType());
    }

    @Test
    public void updateRoleIsLogged() throws Exception {
        LogRecord log = logRecordOf(MaxLabelOrgMemberServiceImpl.class,
                "updateRole", Long.class, Long.class, Integer.class, Integer.class, Long.class);
        assertNotNull(log, "角色变更缺少 @LogRecord");
        assertEquals(MEMBER_ORG_UPDATE_ROLE_SUB_TYPE, log.subType());
    }

    @Test
    public void transferOwnerIsLogged() throws Exception {
        LogRecord log = logRecordOf(MaxLabelOrgMemberServiceImpl.class,
                "transferOwner", Long.class, Long.class, Integer.class, Long.class);
        assertNotNull(log, "OWNER 转交缺少 @LogRecord");
        assertEquals(MEMBER_ORG_TRANSFER_OWNER_SUB_TYPE, log.subType());
    }

    @Test
    public void assignSeatIsLogged() throws Exception {
        LogRecord log = logRecordOf(MaxLabelOrgSeatServiceImpl.class,
                "assign", Long.class, Long.class, Integer.class, Long.class);
        assertNotNull(log, "席位分配缺少 @LogRecord");
        assertEquals(MEMBER_ORG_SEAT_ASSIGN_SUB_TYPE, log.subType());
    }

    @Test
    public void revokeSeatIsLogged() throws Exception {
        LogRecord log = logRecordOf(MaxLabelOrgSeatServiceImpl.class,
                "revoke", Long.class, Long.class, Integer.class, Long.class);
        assertNotNull(log, "席位撤销缺少 @LogRecord");
        assertEquals(MEMBER_ORG_SEAT_REVOKE_SUB_TYPE, log.subType());
    }

}
