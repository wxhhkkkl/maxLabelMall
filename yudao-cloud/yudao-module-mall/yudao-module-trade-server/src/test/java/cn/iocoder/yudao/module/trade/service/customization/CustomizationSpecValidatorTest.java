package cn.iocoder.yudao.module.trade.service.customization;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationFileBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMIZATION_FILE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 设计文件校验（014 / Q92 / Q106）
 */
public class CustomizationSpecValidatorTest {

    private final CustomizationSpecValidator validator = new CustomizationSpecValidator();

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(validator, "fileDomain", "https://img.yuwangchenfa.com");
    }

    private CustomizationFileBO file(String name, String url) {
        CustomizationFileBO file = new CustomizationFileBO();
        file.setName(name);
        file.setUrl(url);
        file.setSize(1024L);
        return file;
    }

    @Test
    public void testValid() {
        validator.validateFiles(List.of(file("a.pdf", "https://img.yuwangchenfa.com/20261101/a.pdf")));
    }

    @Test
    public void testWrongExtensionRejected() {
        ServiceException ex = assertThrows(ServiceException.class, () -> validator.validateFiles(
                List.of(file("a.exe", "https://img.yuwangchenfa.com/20261101/a.exe"))));
        assertEquals(CUSTOMIZATION_FILE_INVALID.getCode(), ex.getCode());
    }

    @Test
    public void testWrongDomainRejected() {
        ServiceException ex = assertThrows(ServiceException.class, () -> validator.validateFiles(
                List.of(file("a.pdf", "https://evil.example.com/a.pdf"))));
        assertEquals(CUSTOMIZATION_FILE_INVALID.getCode(), ex.getCode());
    }

    @Test
    public void testCountRejected() {
        List<CustomizationFileBO> files = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            files.add(file("a.pdf", "https://img.yuwangchenfa.com/" + i + ".pdf"));
        }
        ServiceException ex = assertThrows(ServiceException.class, () -> validator.validateFiles(files));
        assertEquals(CUSTOMIZATION_FILE_INVALID.getCode(), ex.getCode());
    }

    @Test
    public void testSizeRejected() {
        CustomizationFileBO file = file("a.pdf", "https://img.yuwangchenfa.com/a.pdf");
        file.setSize(17L * 1024 * 1024);
        ServiceException ex = assertThrows(ServiceException.class, () -> validator.validateFiles(List.of(file)));
        assertEquals(CUSTOMIZATION_FILE_INVALID.getCode(), ex.getCode());
    }

    @Test
    public void testExtensionFromUrlWhenNameMissing() {
        CustomizationFileBO file = file(null, "https://img.yuwangchenfa.com/20261101/artwork.PNG?sig=1");
        validator.validateFiles(List.of(file));
    }

}
