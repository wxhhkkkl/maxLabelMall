package cn.iocoder.yudao.module.product.service.software;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 软件包存储：目录规则与下载地址白名单（013 / FR-335 / Q85）
 */
public class SoftwareStorageServiceImplTest {

    private SoftwareStorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        storageService = new SoftwareStorageServiceImpl();
        ReflectionTestUtils.setField(storageService, "storageDomain", "img.yuwangchenfa.com");
    }

    @Test
    void buildDirectoryByPlatformArchVersion() {
        assertEquals("software/windows-x64/1.0.23",
                storageService.buildDirectory("windows", "x64", "1.0.23"));
        assertEquals("software/macos-arm64/2.0.0",
                storageService.buildDirectory("macos", "arm64", "2.0.0"));
    }

    @Test
    void httpsOnConfiguredDomainAllowed() {
        assertTrue(storageService.isAllowedUrl("https://img.yuwangchenfa.com/software/windows-x64/1.0.23/a.exe"));
    }

    @Test
    void subdomainOfConfiguredDomainAllowed() {
        assertTrue(storageService.isAllowedUrl("https://cdn.img.yuwangchenfa.com/a.exe"));
    }

    @Test
    void httpRejected() {
        // 打包构建只接受 https（Q85 / resolveUpdateFeedUrl 的同一口径）
        assertFalse(storageService.isAllowedUrl("http://img.yuwangchenfa.com/a.exe"));
    }

    @Test
    void foreignDomainRejected() {
        assertFalse(storageService.isAllowedUrl("https://evil.example.com/a.exe"));
    }

    @Test
    void domainSuffixTrickNotAllowed() {
        // 不能让「img.yuwangchenfa.com.evil.com」这类域名混过去
        assertFalse(storageService.isAllowedUrl("https://img.yuwangchenfa.com.evil.com/a.exe"));
    }

    @Test
    void blankOrInvalidRejected() {
        assertFalse(storageService.isAllowedUrl(null));
        assertFalse(storageService.isAllowedUrl(""));
        assertFalse(storageService.isAllowedUrl("not a url"));
    }

}
