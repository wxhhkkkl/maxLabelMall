package cn.iocoder.yudao.module.product.controller.app.filter;

import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守住 C 端筛选接口的**匿名可访问性**（特性 003 / US15）。
 *
 * ## 为什么要这条测试
 * 2026-10-11 上线后实测发现：`/app-api/product/filter/dimension-list` 返回 **401 账号未登录** ——
 * 因为我漏了 `@PermitAll`（既有的三个 app 商品接口都有）。
 *
 * 症状**不是报错，而是静默**：前端 catch 掉 401 之后筛选条整块不渲染，
 * 排查时只会看到"这个分类就是没配维度吧"，一路查错方向。
 *
 * ## 为什么该由测试兜，而不是靠人记住
 * `@PermitAll` 是 Spring Security 在**运行时**生效的，任何"调 service 层"的单测都碰不到它 ——
 * 上面那 46 条测试全绿，这个 bug 照样溜到了生产。反射查注解是唯一便宜又能生效的守法。
 */
public class ProductFilterControllerPermitAllTest {

    @Test
    public void testAllAppEndpointsAreAnonymous() {
        // 「C 端浏览商品不要求登录」是本项目既有的口径（category / spu / comment 三个 app 接口都是
        // @PermitAll）。新的筛选接口没有任何理由例外。
        List<Method> appEndpoints = Arrays.stream(ProductFilterController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .toList();

        assertFalse(appEndpoints.isEmpty(), "没找到任何 @GetMapping —— 测试本身可能失效了，别放过");

        List<String> missing = appEndpoints.stream()
                .filter(method -> !method.isAnnotationPresent(PermitAll.class))
                .map(Method::getName)
                .toList();

        assertTrue(missing.isEmpty(),
                "这些 C 端筛选接口缺 @PermitAll，上线后会返回 401 且**静默**"
                        + "（前端只当没配维度，排查会一路走错方向）：" + missing
                        + " —— 见 ProductFilterController 里的注释");
    }

}
