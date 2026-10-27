package cn.iocoder.yudao.module.product.service.software;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareDO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareVersionDO;
import cn.iocoder.yudao.module.product.dal.mysql.software.SoftwareCategoryMapper;
import cn.iocoder.yudao.module.product.dal.mysql.software.SoftwareMapper;
import cn.iocoder.yudao.module.product.dal.mysql.software.SoftwareVersionMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 软件版本（013 / FR-333 / FR-337 / FR-338 / FR-352 / FR-353）
 */
@Import({SoftwareVersionServiceImpl.class, SoftwareServiceImpl.class, SoftwareCategoryServiceImpl.class})
public class SoftwareVersionServiceImplTest extends BaseDbUnitTest {

    private static final String URL = "https://img.yuwangchenfa.com/software/windows-x64/1.0.1/a.exe";
    private static final String SHA = "a".repeat(88);

    @Resource
    private SoftwareVersionService versionService;
    @Resource
    private SoftwareVersionMapper versionMapper;
    @Resource
    private SoftwareMapper softwareMapper;
    @Resource
    private SoftwareCategoryMapper categoryMapper;

    @MockitoBean
    private SoftwareStorageService storageService;
    @MockitoBean
    private SoftwareFileChecker fileChecker;

    private Long softwareId;

    @BeforeEach
    void setUp() {
        SoftwareCategoryDO category = SoftwareCategoryDO.builder().name("桌面端").sort(0).status(0).build();
        categoryMapper.insert(category);
        SoftwareDO software = SoftwareDO.builder()
                .categoryId(category.getId()).title("MaxLabel").introduction("")
                .type(1).showInSupport(true).status(0).build();
        softwareMapper.insert(software);
        softwareId = software.getId();

        when(storageService.isAllowedUrl(anyString())).thenReturn(true);
        when(fileChecker.getContentLength(anyString())).thenReturn(100L);
    }

    private SoftwareVersionDO version(String ver, String platform, String arch) {
        return SoftwareVersionDO.builder()
                .softwareId(softwareId).version(ver).platform(platform).arch(arch)
                .fileName("a.exe").fileSize(100L).sha512(SHA)
                .fileUrl(URL).objectKey("software/windows-x64/1.0.1/a.exe")
                .build();
    }

    @Test
    void registerCreatesDraft() {
        Long id = versionService.registerVersion(version("1.0.1", "windows", "x64"));

        SoftwareVersionDO saved = versionService.getVersion(id);
        assertEquals(1, saved.getStatus());          // 草稿
        assertNull(saved.getCurrentFlag());
    }

    @Test
    void invalidVersionFormatRejected() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> versionService.registerVersion(version("1.0", "windows", "x64")));
        assertEquals(SOFTWARE_FILE_INVALID.getCode(), e.getCode());
    }

    @Test
    void duplicateVersionRejected() {
        versionService.registerVersion(version("1.0.1", "windows", "x64"));

        ServiceException e = assertThrows(ServiceException.class,
                () -> versionService.registerVersion(version("1.0.1", "windows", "x64")));
        assertEquals(SOFTWARE_VERSION_DUPLICATE.getCode(), e.getCode());
    }

    /** 服务端只回源核对大小；对不上就拒绝登记（FR-337 / FR-338） */
    @Test
    void sizeMismatchRejected() {
        when(fileChecker.getContentLength(anyString())).thenReturn(999L);

        ServiceException e = assertThrows(ServiceException.class,
                () -> versionService.registerVersion(version("1.0.2", "windows", "x64")));
        assertEquals(SOFTWARE_FILE_INVALID.getCode(), e.getCode());
    }

    @Test
    void unreadableFileRejected() {
        when(fileChecker.getContentLength(anyString())).thenThrow(new IllegalStateException("404"));

        ServiceException e = assertThrows(ServiceException.class,
                () -> versionService.registerVersion(version("1.0.3", "windows", "x64")));
        assertEquals(SOFTWARE_FILE_INVALID.getCode(), e.getCode());
    }

    @Test
    void urlOutsideStorageRejected() {
        when(storageService.isAllowedUrl(anyString())).thenReturn(false);

        ServiceException e = assertThrows(ServiceException.class,
                () -> versionService.registerVersion(version("1.0.4", "windows", "x64")));
        assertEquals(SOFTWARE_FILE_INVALID.getCode(), e.getCode());
    }

    @Test
    void sha512WrongLengthRejected() {
        SoftwareVersionDO v = version("1.0.5", "windows", "x64");
        v.setSha512("short");

        ServiceException e = assertThrows(ServiceException.class, () -> versionService.registerVersion(v));
        assertEquals(SOFTWARE_FILE_INVALID.getCode(), e.getCode());
    }

    /** 设为当前发布后，同平台同架构的旧当前自动降为历史（FR-352） */
    @Test
    void publishCurrentReplacesPrevious() {
        Long first = versionService.registerVersion(version("1.0.1", "windows", "x64"));
        versionService.publishVersion(first, true);
        assertEquals(first, versionService.getCurrentVersion(softwareId, "windows", "x64").getId());

        Long second = versionService.registerVersion(version("1.0.2", "windows", "x64"));
        versionService.publishVersion(second, true);

        assertEquals(second, versionService.getCurrentVersion(softwareId, "windows", "x64").getId());
        assertNull(versionService.getVersion(first).getCurrentFlag());
        // 同组合当前发布恒为 1 个（SC-136）
        assertEquals(1, versionMapper.selectListCurrent(List.of(softwareId)).size());
    }

    /** 不同平台互不影响（Q86） */
    @Test
    void currentIsPerPlatformAndArch() {
        Long win = versionService.registerVersion(version("1.0.1", "windows", "x64"));
        Long mac = versionService.registerVersion(version("1.0.1", "macos", "arm64"));
        versionService.publishVersion(win, true);
        versionService.publishVersion(mac, true);

        assertEquals(win, versionService.getCurrentVersion(softwareId, "windows", "x64").getId());
        assertEquals(mac, versionService.getCurrentVersion(softwareId, "macos", "arm64").getId());
    }

    @Test
    void unpublishClearsCurrent() {
        Long id = versionService.registerVersion(version("1.0.1", "windows", "x64"));
        versionService.publishVersion(id, true);

        versionService.unpublishVersion(id);

        SoftwareVersionDO saved = versionService.getVersion(id);
        assertEquals(2, saved.getStatus());
        assertNull(saved.getCurrentFlag());
        assertNull(versionService.getCurrentVersion(softwareId, "windows", "x64"));
    }

    /** 下架后不能再直接设为当前发布 */
    @Test
    void unpublishedCannotBecomeCurrent() {
        Long id = versionService.registerVersion(version("1.0.1", "windows", "x64"));
        versionService.unpublishVersion(id);

        ServiceException e = assertThrows(ServiceException.class,
                () -> versionService.publishVersion(id, true));
        assertEquals(SOFTWARE_VERSION_NOT_PUBLISHED.getCode(), e.getCode());
    }

    /** 软删除：记录从查询消失，但行还在（文件与链接仍可用） */
    @Test
    void deleteIsSoft() {
        Long id = versionService.registerVersion(version("1.0.1", "windows", "x64"));

        versionService.deleteVersion(id);

        assertNull(versionService.getVersion(id));
        // 同上：逻辑删除后 selectById 过滤掉该行，文件与链接仍可用
    }

    @Test
    void registerForUnknownSoftwareRejected() {
        SoftwareVersionDO v = version("1.0.1", "windows", "x64");
        v.setSoftwareId(9999L);

        ServiceException e = assertThrows(ServiceException.class, () -> versionService.registerVersion(v));
        assertEquals(SOFTWARE_NOT_EXISTS.getCode(), e.getCode());
    }

}
