package cn.iocoder.yudao.module.member.controller.admin.license;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 授权看板的**服务端权限**（020 / FR-556 / T084）
 *
 * 这里不用起 Spring 上下文：要防的只是"有人把 @PreAuthorize 删了"这类回归 ——
 * 前端藏按钮不算数，接口必须自己拦。
 */
public class LicenseDashboardControllerPermissionTest {

    private static final String QUERY = "@ss.hasPermission('member:license:query')";
    private static final String GIFT = "@ss.hasPermission('member:license:gift')";

    private static String permissionOf(String methodName) throws NoSuchMethodException {
        Method method = Arrays.stream(LicenseDashboardController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(methodName));
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, methodName + " 必须挂 @PreAuthorize");
        return annotation.value();
    }

    @Test
    public void testSummary_requiresQueryPermission() throws Exception {
        assertEquals(QUERY, permissionOf("getSummary"));
    }

    @Test
    public void testCustomerPage_requiresQueryPermission() throws Exception {
        assertEquals(QUERY, permissionOf("getCustomerPage"));
    }

    @Test
    public void testGrantPage_requiresQueryPermission() throws Exception {
        assertEquals(QUERY, permissionOf("getGrantPage"));
    }

    @Test
    public void testGrant_requiresGiftPermissionNotQuery() throws Exception {
        // 赠送会改变客户的商业权益，必须与"看"分开授权（R7）
        assertEquals(GIFT, permissionOf("grant"));
    }

    @Test
    public void testGiftTargetPage_requiresGiftPermission() throws Exception {
        // 能送的人必须能选到对象（含从未授权的客户），但不该借 query 权限扩大范围（Q162）
        assertEquals(GIFT, permissionOf("getGiftTargetPage"));
    }

}
