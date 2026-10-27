package cn.iocoder.yudao.module.product.controller.app.software;

import cn.iocoder.yudao.module.product.controller.app.software.vo.AppSoftwareUpdateCheckRespVO;
import cn.iocoder.yudao.module.product.service.software.SoftwareUpdateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 客户端更新接口（013 / FR-334 / R82）
 */
@ExtendWith(MockitoExtension.class)
public class AppSoftwareUpdateControllerTest {

    @Mock
    private SoftwareUpdateService updateService;
    @InjectMocks
    private AppSoftwareUpdateController controller;

    @Test
    void checkDelegatesAndReturnsMetadata() {
        AppSoftwareUpdateCheckRespVO vo = new AppSoftwareUpdateCheckRespVO();
        vo.setHasUpdate(true);
        vo.setLatestVersion("1.0.23");
        vo.setFeedUrl("https://new.yuwangchenfa.com/app-api/software/feed/windows/x64/");
        when(updateService.checkUpdate(10L, "1.0.22", "windows", "x64")).thenReturn(vo);

        AppSoftwareUpdateCheckRespVO result = controller.checkUpdate(10L, "1.0.22", "windows", "x64").getData();

        assertTrue(result.getHasUpdate());
        assertEquals("1.0.23", result.getLatestVersion());
    }

    @Test
    void latestYmlServedWithNoCache() {
        when(updateService.renderLatestYml("windows", "x64")).thenReturn("version: 1.0.23\npath: https://x/a.exe\n");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String body = controller.getLatestYml("windows", "x64", response);

        assertEquals("no-cache", response.getHeader("Cache-Control"));
        assertEquals(200, response.getStatus());
        assertTrue(body.contains("version: 1.0.23"));
    }

    /** 无当前发布 → 404，让 electron-updater 明确「没有更新」而不是拿到空 yml */
    @Test
    void latestYmlReturns404WhenNoCurrentRelease() {
        when(updateService.renderLatestYml("macos", "arm64")).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String body = controller.getLatestYml("macos", "arm64", response);

        assertEquals(404, response.getStatus());
        assertEquals("", body);
    }

}
