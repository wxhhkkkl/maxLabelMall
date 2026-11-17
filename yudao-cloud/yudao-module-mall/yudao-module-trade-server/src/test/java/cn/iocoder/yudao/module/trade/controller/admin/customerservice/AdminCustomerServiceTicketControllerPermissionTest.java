package cn.iocoder.yudao.module.trade.controller.admin.customerservice;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 投诉咨询服务端权限（021 / FR-578）
 */
public class AdminCustomerServiceTicketControllerPermissionTest {

    private static final String QUERY = "@ss.hasPermission('trade:customer-service:query')";
    private static final String HANDLE = "@ss.hasPermission('trade:customer-service:handle')";

    private static String permissionOf(String methodName) throws NoSuchMethodException {
        Method method = Arrays.stream(AdminCustomerServiceTicketController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(methodName));
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, methodName + " 必须挂 @PreAuthorize");
        return annotation.value();
    }

    @Test
    public void testPage_requiresQueryPermission() throws Exception {
        assertEquals(QUERY, permissionOf("page"));
    }

    @Test
    public void testGet_requiresQueryPermission() throws Exception {
        assertEquals(QUERY, permissionOf("get"));
    }

    @Test
    public void testHandle_requiresHandlePermission() throws Exception {
        assertEquals(HANDLE, permissionOf("handle"));
    }

}
