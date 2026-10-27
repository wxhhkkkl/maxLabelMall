package cn.iocoder.yudao.module.product.service.software;

import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareDO;
import cn.iocoder.yudao.module.product.enums.software.SoftwareLogRecordConstants;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * FR-346：运营在软件与版本上的关键操作（创建、发布、下架、删除）必须记录操作人与时间。
 *
 * 这里用反射守住注解本身 —— 真正的落库由 yudao 的 operate-log 框架完成；
 * 单测里没有 AOP 容器，断言注解不丢失即可防住「改代码时顺手删掉」的回归。
 */
public class SoftwareAuditLogTest {

    private LogRecord logOf(Class<?> clazz, String method, Class<?>... params) throws Exception {
        return clazz.getMethod(method, params).getAnnotation(LogRecord.class);
    }

    @Test
    void createSoftwareIsLogged() throws Exception {
        LogRecord log = logOf(SoftwareServiceImpl.class, "createSoftware", SoftwareDO.class);
        assertNotNull(log, "软件创建缺少 @LogRecord");
        assertFalse(log.subType().isBlank());
    }

    @Test
    void deleteSoftwareIsLogged() throws Exception {
        LogRecord log = logOf(SoftwareServiceImpl.class, "deleteSoftware", Long.class);
        assertNotNull(log, "软件删除缺少 @LogRecord");
        assertFalse(log.subType().isBlank());
    }

    @Test
    void publishVersionIsLogged() throws Exception {
        LogRecord log = logOf(SoftwareVersionServiceImpl.class, "publishVersion", Long.class, boolean.class);
        assertNotNull(log, "版本发布缺少 @LogRecord");
        assertEquals(SoftwareLogRecordConstants.SOFTWARE_VERSION_PUBLISH_SUB_TYPE, log.subType());
    }

    @Test
    void unpublishVersionIsLogged() throws Exception {
        LogRecord log = logOf(SoftwareVersionServiceImpl.class, "unpublishVersion", Long.class);
        assertNotNull(log, "版本下架缺少 @LogRecord");
        assertEquals(SoftwareLogRecordConstants.SOFTWARE_VERSION_UNPUBLISH_SUB_TYPE, log.subType());
    }

    @Test
    void deleteVersionIsLogged() throws Exception {
        LogRecord log = logOf(SoftwareVersionServiceImpl.class, "deleteVersion", Long.class);
        assertNotNull(log, "版本删除缺少 @LogRecord");
        assertEquals(SoftwareLogRecordConstants.SOFTWARE_VERSION_DELETE_SUB_TYPE, log.subType());
    }

}
