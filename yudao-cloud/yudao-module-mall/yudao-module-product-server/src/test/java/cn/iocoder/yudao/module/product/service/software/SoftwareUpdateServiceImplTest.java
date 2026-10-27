package cn.iocoder.yudao.module.product.service.software;

import cn.iocoder.yudao.module.product.controller.app.software.vo.AppSoftwareUpdateCheckRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareDO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareVersionDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 客户端更新判定（013 / FR-334 / FR-347 / FR-350 / FR-355 / Q90）
 */
@ExtendWith(MockitoExtension.class)
public class SoftwareUpdateServiceImplTest {

    private static final String URL = "https://img.yuwangchenfa.com/software/windows-x64/1.0.23/a.exe";

    @Mock
    private SoftwareService softwareService;
    @Mock
    private SoftwareVersionService versionService;
    @InjectMocks
    private SoftwareUpdateServiceImpl updateService;

    private SoftwareDO software;
    private SoftwareVersionDO current;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(updateService, "publicBaseUrl", "https://new.yuwangchenfa.com");
        software = SoftwareDO.builder().id(10L).title("MaxLabel").type(1).status(0).showInSupport(true).build();
        current = SoftwareVersionDO.builder()
                .id(1L).softwareId(10L).version("1.0.23").platform("windows").arch("x64")
                .fileUrl(URL).sha512("S").fileSize(100L).status(0).currentFlag(1)
                .releaseTime(LocalDateTime.of(2026, 10, 27, 10, 0)).build();
    }

    private void stubCurrent(SoftwareVersionDO version) {
        when(softwareService.getEnabledDesktopSoftware(10L)).thenReturn(software);
        when(versionService.getCurrentVersion(10L, "windows", "x64")).thenReturn(version);
    }

    @Test
    void olderClientGetsUpdate() {
        stubCurrent(current);

        AppSoftwareUpdateCheckRespVO vo = updateService.checkUpdate(10L, "1.0.22", "windows", "x64");

        assertTrue(vo.getHasUpdate());
        assertEquals("1.0.23", vo.getLatestVersion());
        assertEquals(100L, vo.getSize());
        assertEquals("https://new.yuwangchenfa.com/app-api/software/feed/windows/x64/", vo.getFeedUrl());
    }

    /** Q90：客户端版本等于当前发布 → 无更新 */
    @Test
    void sameVersionIsUpToDate() {
        stubCurrent(current);

        assertFalse(updateService.checkUpdate(10L, "1.0.23", "windows", "x64").getHasUpdate());
    }

    /** Q90：运营回滚后，已升级的客户端不降级、不打扰 */
    @Test
    void newerClientDoesNotDowngrade() {
        stubCurrent(current);

        AppSoftwareUpdateCheckRespVO vo = updateService.checkUpdate(10L, "1.0.30", "windows", "x64");

        assertFalse(vo.getHasUpdate());
        assertNull(vo.getFeedUrl());
    }

    @Test
    void versionComparisonIsNumeric() {
        // 1.0.9 < 1.0.10（字符串比较会判错）
        assertTrue(SoftwareUpdateServiceImpl.isNewer("1.0.9", "1.0.10"));
        assertFalse(SoftwareUpdateServiceImpl.isNewer("1.0.10", "1.0.9"));
    }

    @Test
    void invalidClientVersionTreatedAsNoUpdate() {
        stubCurrent(current);

        assertFalse(updateService.checkUpdate(10L, "abc", "windows", "x64").getHasUpdate());
        assertFalse(updateService.checkUpdate(10L, "1.0", "windows", "x64").getHasUpdate());
    }

    /** FR-350：平台/架构不匹配不下发任何文件 */
    @Test
    void unknownPlatformOrArchGetsNoUpdate() {
        assertFalse(updateService.checkUpdate(10L, "1.0.22", "solaris", "x64").getHasUpdate());
        assertFalse(updateService.checkUpdate(10L, "1.0.22", "windows", "mips").getHasUpdate());
    }

    @Test
    void noCurrentReleaseGetsNoUpdate() {
        when(softwareService.getEnabledDesktopSoftware(10L)).thenReturn(software);
        when(versionService.getCurrentVersion(10L, "windows", "x64")).thenReturn(null);

        assertFalse(updateService.checkUpdate(10L, "1.0.22", "windows", "x64").getHasUpdate());
    }

    @Test
    void disabledOrMissingSoftwareGetsNoUpdate() {
        when(softwareService.getEnabledDesktopSoftware(10L)).thenReturn(null);

        assertFalse(updateService.checkUpdate(10L, "1.0.22", "windows", "x64").getHasUpdate());
    }

    @Test
    void defaultSoftwareUsedWhenIdMissing() {
        when(softwareService.getDefaultDesktopSoftware()).thenReturn(software);
        when(versionService.getCurrentVersion(10L, "windows", "x64")).thenReturn(current);

        assertTrue(updateService.checkUpdate(null, "1.0.22", "windows", "x64").getHasUpdate());
    }

    @Test
    void latestYmlContainsOssAbsolutePath() {
        when(softwareService.getDefaultDesktopSoftware()).thenReturn(software);
        when(versionService.getCurrentVersion(10L, "windows", "x64")).thenReturn(current);

        String yml = updateService.renderLatestYml("windows", "x64");

        assertNotNull(yml);
        assertTrue(yml.contains("version: 1.0.23"));
        assertTrue(yml.contains("path: " + URL));
        assertTrue(yml.contains("sha512: S"));
        assertTrue(yml.contains("size: 100"));
        assertTrue(yml.contains("releaseDate: '"));  // 具体时区随机器而变，只断言存在
    }

    @Test
    void latestYmlNullWhenNoCurrentRelease() {
        when(softwareService.getDefaultDesktopSoftware()).thenReturn(null);

        assertNull(updateService.renderLatestYml("windows", "x64"));
    }

}
