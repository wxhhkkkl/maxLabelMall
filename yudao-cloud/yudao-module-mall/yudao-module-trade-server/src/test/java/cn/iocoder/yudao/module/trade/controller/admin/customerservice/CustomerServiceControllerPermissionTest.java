package cn.iocoder.yudao.module.trade.controller.admin.customerservice;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 客户服务管理端权限全覆盖（021 / FR-578 / T059）
 *
 * 前端藏按钮不算数，服务端每个接口都必须自己拦。
 */
public class CustomerServiceControllerPermissionTest {

    private static final String QUERY = "@ss.hasPermission('trade:customer-service:query')";
    private static final String HANDLE = "@ss.hasPermission('trade:customer-service:handle')";
    private static final String REFUND = "@ss.hasPermission('trade:customer-service:refund')";

    private static String permissionOf(Class<?> clazz, String methodName) throws NoSuchMethodException {
        Method method = Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(clazz.getSimpleName() + "#" + methodName));
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, clazz.getSimpleName() + "#" + methodName + " 必须挂 @PreAuthorize");
        return annotation.value();
    }

    @Test
    public void testTicket() throws Exception {
        assertEquals(QUERY, permissionOf(AdminCustomerServiceTicketController.class, "page"));
        assertEquals(QUERY, permissionOf(AdminCustomerServiceTicketController.class, "get"));
        assertEquals(HANDLE, permissionOf(AdminCustomerServiceTicketController.class, "handle"));
        assertEquals(HANDLE, permissionOf(AdminCustomerServiceTicketController.class, "create"));
    }

    @Test
    public void testRepair() throws Exception {
        assertEquals(QUERY, permissionOf(AdminCustomerServiceRepairController.class, "page"));
        assertEquals(QUERY, permissionOf(AdminCustomerServiceRepairController.class, "get"));
        assertEquals(HANDLE, permissionOf(AdminCustomerServiceRepairController.class, "create"));
        assertEquals(HANDLE, permissionOf(AdminCustomerServiceRepairController.class, "contact"));
        assertEquals(HANDLE, permissionOf(AdminCustomerServiceRepairController.class, "createOrder"));
        assertEquals(HANDLE, permissionOf(AdminCustomerServiceRepairController.class, "updateStatus"));
        assertEquals(HANDLE, permissionOf(AdminCustomerServiceRepairController.class, "cancel"));
    }

    @Test
    public void testRepairOrder() throws Exception {
        assertEquals(HANDLE, permissionOf(AdminCustomerServiceRepairOrderController.class, "confirmOffline"));
        assertEquals(QUERY, permissionOf(AdminCustomerServiceRepairOrderController.class, "get"));
        assertEquals(QUERY, permissionOf(AdminCustomerServiceRepairOrderController.class, "page"));
        assertEquals(REFUND, permissionOf(AdminCustomerServiceRepairOrderController.class, "refund"));
        assertEquals(HANDLE, permissionOf(AdminCustomerServiceRepairOrderController.class, "complete"));
    }

    @Test
    public void testAddress() throws Exception {
        assertEquals(QUERY, permissionOf(AdminCustomerServiceAddressController.class, "getAddress"));
        assertEquals(HANDLE, permissionOf(AdminCustomerServiceAddressController.class, "updateAddress"));
    }

    @Test
    public void testMemberSearch() throws Exception {
        assertEquals(HANDLE, permissionOf(AdminCustomerServiceMemberController.class, "search"));
    }

}
