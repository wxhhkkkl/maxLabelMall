package cn.iocoder.yudao.module.product.service.category;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.mysql.category.ProductCategoryMapper;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ProductCategoryService#scopeOf(Long)} 的单元测试。
 *
 * ⚠️ **这是本模块（yudao-module-product-server）的第一个测试类**（2026-10-11，特性 003 / 任务 T256）。
 * 它同时兼作"测试脚手架能不能跑"的验证 —— 该模块此前 0 个测试，`create_tables.sql` 从没被真正执行过。
 *
 * `scopeOf` 是「分类级筛选维度」的地基：三个用户故事各用它一次 ——
 * US13 查重名、US14 查商品可挂的辖域、US15 查前台要渲染哪些维度。
 * 它错了，三处会一起错，而且**不报错**，所以这里把边界一次测干净。
 */
@Import(ProductCategoryServiceImpl.class)
public class ProductCategoryScopeTest extends BaseDbUnitTest {

    @Resource
    private ProductCategoryService categoryService;

    @Resource
    private ProductCategoryMapper categoryMapper;

    @MockitoBean
    private ProductSpuService productSpuService; // 本测试用不到，但 Impl 里注入了它

    /** 造一个分类，直接插库 —— 绕过 Service 的层级校验，因为成环那种数据本来就过不了校验 */
    private Long insertCategory(Long id, Long parentId, String name) {
        ProductCategoryDO category = ProductCategoryDO.builder()
                .id(id).parentId(parentId).name(name)
                .picUrl("") // NOT NULL 列，测试建表脚本里没给默认值
                .sort(0).status(0).build();
        categoryMapper.insert(category);
        return id;
    }

    @Test
    public void testScopeOf_threeLevelChain() {
        // 准备：线上就是这个形状 —— 碳带(113) → 蜡基(114)
        insertCategory(1L, 0L, "根分类");
        insertCategory(113L, 1L, "碳带");
        insertCategory(114L, 113L, "蜡基");

        // 调用
        Set<Long> scope = categoryService.scopeOf(114L);

        // 断言：自身 + 全部祖先（顺序不重要，用集合比）
        assertEquals(new HashSet<>(java.util.List.of(1L, 113L, 114L)), scope);
    }

    @Test
    public void testScopeOf_rootCategory() {
        // 准备：根分类，parentId 为 0
        insertCategory(113L, 0L, "碳带");

        // 调用
        Set<Long> scope = categoryService.scopeOf(113L);

        // 断言：只有它自己，**不得**把 0 也带进来
        assertEquals(new HashSet<>(java.util.List.of(113L)), scope);
    }

    @Test
    public void testScopeOf_notExists() {
        // 调用：一个不存在的分类
        Set<Long> scope = categoryService.scopeOf(999L);

        // 断言：不抛异常，只返回它自己（调用方据此查不到任何维度，自然就是"没配"）
        assertEquals(new HashSet<>(java.util.List.of(999L)), scope);
    }

    @Test
    public void testScopeOf_brokenChain() {
        // 准备：parentId 指向一个不存在的分类（数据脏了）
        insertCategory(500L, 404L, "断链分类");

        // 调用
        Set<Long> scope = categoryService.scopeOf(500L);

        // 断言：走到哪算哪，不抛异常
        assertEquals(new HashSet<>(java.util.List.of(500L)), scope);
    }

    @Test
    public void testScopeOf_cycleDoesNotHang() {
        // 准备：A.parentId = B，B.parentId = A —— 数据异常时的死循环风险
        insertCategory(601L, 602L, "A");
        insertCategory(602L, 601L, "B");

        // 调用：**必须在有限步内返回**。这里用 JUnit 的超时把"挂住"变成失败而不是卡死整个测试套件
        Set<Long> scope = assertTimeoutPreemptively(java.time.Duration.ofSeconds(5),
                () -> categoryService.scopeOf(601L));

        // 断言：两个都在集合里，且集合有限
        assertEquals(new HashSet<>(java.util.List.of(601L, 602L)), scope);
    }

}
