package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMER_SERVICE_FILE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 客户服务图片附件校验（021 / FR-580）
 */
public class CustomerServiceFileValidatorTest {

    private final CustomerServiceFileValidator validator = new CustomerServiceFileValidator();

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(validator, "fileDomain", "https://img.yuwangchenfa.com");
    }

    @Test
    public void testValid() {
        validator.validate(List.of(
                "https://img.yuwangchenfa.com/20261117/a.jpg",
                "https://img.yuwangchenfa.com/20261117/b.PNG?x=1"));
        validator.validate(null);
    }

    @Test
    public void testTooManyRejected() {
        List<String> images = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            images.add("https://img.yuwangchenfa.com/" + i + ".jpg");
        }
        ServiceException ex = assertThrows(ServiceException.class, () -> validator.validate(images));
        assertEquals(CUSTOMER_SERVICE_FILE_INVALID.getCode(), ex.getCode());
    }

    @Test
    public void testWrongExtensionRejected() {
        ServiceException ex = assertThrows(ServiceException.class, () -> validator.validate(
                List.of("https://img.yuwangchenfa.com/a.exe")));
        assertEquals(CUSTOMER_SERVICE_FILE_INVALID.getCode(), ex.getCode());
    }

    @Test
    public void testWrongDomainRejected() {
        ServiceException ex = assertThrows(ServiceException.class, () -> validator.validate(
                List.of("https://evil.example.com/a.jpg")));
        assertEquals(CUSTOMER_SERVICE_FILE_INVALID.getCode(), ex.getCode());
    }

    @Test
    public void testBlankUrlRejected() {
        ServiceException ex = assertThrows(ServiceException.class, () -> validator.validate(List.of(" ")));
        assertEquals(CUSTOMER_SERVICE_FILE_INVALID.getCode(), ex.getCode());
    }

}
