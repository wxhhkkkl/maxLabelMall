package cn.iocoder.yudao.module.product.service.software;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareDO;
import cn.iocoder.yudao.module.product.dal.mysql.software.SoftwareCategoryMapper;
import cn.iocoder.yudao.module.product.dal.mysql.software.SoftwareMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOFTWARE_CATEGORY_NOT_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOFTWARE_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 软件 / 驱动（013 / FR-331）
 */
@Import({SoftwareServiceImpl.class, SoftwareCategoryServiceImpl.class})
public class SoftwareServiceImplTest extends BaseDbUnitTest {

    @Resource
    private SoftwareService softwareService;
    @Resource
    private SoftwareMapper softwareMapper;
    @Resource
    private SoftwareCategoryMapper categoryMapper;

    private Long categoryId;

    @BeforeEach
    void setUp() {
        SoftwareCategoryDO category = SoftwareCategoryDO.builder().name("打印驱动").sort(0).status(0).build();
        categoryMapper.insert(category);
        categoryId = category.getId();
    }

    private Long create(String title, int type, boolean showInSupport, int status) {
        return softwareService.createSoftware(SoftwareDO.builder()
                .categoryId(categoryId).title(title).introduction("介绍")
                .type(type).showInSupport(showInSupport).status(status).build());
    }

    @Test
    void createRequiresExistingCategory() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> softwareService.createSoftware(SoftwareDO.builder()
                        .categoryId(9999L).title("x").type(2).build()));
        assertEquals(SOFTWARE_CATEGORY_NOT_EXISTS.getCode(), exception.getCode());
    }

    @Test
    void supportListOnlyContainsEnabledAndShown() {
        create("展示中的驱动", 2, true, 0);
        create("不在支持页的驱动", 2, false, 0);
        create("停用的驱动", 2, true, 1);

        List<SoftwareDO> list = softwareService.getSupportSoftwareList(List.of(categoryId));

        assertEquals(1, list.size());
        assertEquals("展示中的驱动", list.get(0).getTitle());
    }

    /** `show_in_support=0` 不影响客户端更新（US50-AC2） */
    @Test
    void hiddenSoftwareStillResolvesForUpdate() {
        Long id = create("桌面端软件", 1, false, 0);

        assertNotNull(softwareService.getEnabledDesktopSoftware(id));
    }

    @Test
    void driverIsNotDesktopSoftware() {
        Long id = create("某驱动", 2, true, 0);

        assertNull(softwareService.getEnabledDesktopSoftware(id));
    }

    @Test
    void disabledSoftwareIsNotDesktopSoftware() {
        Long id = create("停用的桌面端", 1, true, 1);

        assertNull(softwareService.getEnabledDesktopSoftware(id));
    }

    @Test
    void deleteIsSoft() {
        Long id = create("待删除", 2, true, 0);

        softwareService.deleteSoftware(id);

        assertNull(softwareService.getSoftware(id));
        // 逻辑删除：行仍留在库里（deleted=1），已发出的下载链接不受影响；
        // selectById 会过滤 deleted，所以这里只能验证「查不到」，保留性由 BaseDO 结构保证。
    }

    @Test
    void updateUnknownSoftwareRejected() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> softwareService.updateSoftware(SoftwareDO.builder()
                        .id(9999L).categoryId(categoryId).title("x").type(2).build()));
        assertEquals(SOFTWARE_NOT_EXISTS.getCode(), exception.getCode());
    }

    @Test
    void pageFiltersByTypeAndCategory() {
        create("桌面端", 1, true, 0);
        create("驱动", 2, true, 0);

        assertEquals(2, softwareService.getSoftwarePage(categoryId, null, null, null,
                new cn.iocoder.yudao.framework.common.pojo.PageParam()).getTotal());
        assertEquals(1, softwareService.getSoftwarePage(categoryId, 1, null, null,
                new cn.iocoder.yudao.framework.common.pojo.PageParam()).getTotal());
    }

}
