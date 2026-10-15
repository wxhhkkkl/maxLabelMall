package cn.iocoder.yudao.module.trade.service.invoice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceApplyBaseVO;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceTitleSaveReqVO;
import cn.iocoder.yudao.module.trade.enums.invoice.InvoiceTypeEnum;
import org.junit.jupiter.api.Test;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link InvoiceValidator} 的单元测试（T378 / Q27 / FR-155 / FR-156）。
 *
 * 这是**票种字段矩阵的可执行定义** —— 两条最要紧的断言：
 * · 普票**只填名称必须通过**（只开普票的客户不该被迫填开户行）
 * · 专票**只填名称必须被拒**，且报错要**指明缺哪一项**（FR-156 要求客户知道去补什么）
 *
 * 纯函数测试：不需要 Spring、不需要 H2。
 */
class InvoiceValidatorTest {

    private AppInvoiceApplyBaseVO apply(Integer type, String titleName, String taxNo,
                                        String titleAddress, String titlePhone, String bankName) {
        AppInvoiceApplyBaseVO vo = new AppInvoiceApplyBaseVO();
        vo.setType(type);
        vo.setTitleName(titleName);
        vo.setTaxNo(taxNo);
        vo.setTitleAddress(titleAddress);
        vo.setTitlePhone(titlePhone);
        vo.setBankName(bankName);
        return vo;
    }

    /** 一张填满的专票（各用例按需改其中一项） */
    private AppInvoiceApplyBaseVO fullSpecialInvoice() {
        return apply(InvoiceTypeEnum.E_SPECIAL.getType(), "南京路桥机械有限公司", "91320104660690975E",
                "南京市玄武区某某路 1 号", "18913984596", "工商银行南京分行 6222 0000 0000 0000");
    }

    // ==================== 普票：宽松 ====================

    @Test
    void testValidateApply_normal_onlyTitleName_pass() {
        // 只填名称 —— 必须通过（Q27）
        assertDoesNotThrow(() -> InvoiceValidator.validateApply(
                apply(InvoiceTypeEnum.E_NORMAL.getType(), "张三", null, null, null, null)));

        // 名称 + 税号（企业开普票的常见形态）
        assertDoesNotThrow(() -> InvoiceValidator.validateApply(
                apply(InvoiceTypeEnum.E_NORMAL.getType(), "南京路桥机械有限公司", "91320104660690975E",
                        null, null, null)));
    }

    @Test
    void testValidateApply_normal_taxNoFormatStillChecked() {
        // 普票的税号是选填，但**填了就要合法** —— 否则开出来的票是废的
        ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validateApply(
                apply(InvoiceTypeEnum.E_NORMAL.getType(), "南京路桥机械有限公司", "123", null, null, null)));
        assertEquals(INVOICE_TAX_NO_INVALID.getCode(), e.getCode());
    }

    // ==================== 专票：五项全要 ====================

    @Test
    void testValidateApply_special_allFields_pass() {
        assertDoesNotThrow(() -> InvoiceValidator.validateApply(fullSpecialInvoice()));
    }

    @Test
    void testValidateApply_special_onlyTitleName_fail_andPointOutMissingField() {
        // 一次只缺一项时，报错必须点名是哪一项
        ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validateApply(
                apply(InvoiceTypeEnum.E_SPECIAL.getType(), "南京路桥机械有限公司", null, null, null, null)));
        assertEquals(INVOICE_TITLE_FIELD_REQUIRED.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("纳税人识别号"), "报错要点名缺哪一项，实际：" + e.getMessage());

        // 缺地址
        AppInvoiceApplyBaseVO noAddress = fullSpecialInvoice();
        noAddress.setTitleAddress("  "); // 全空格 = 没填（要 trim）
        ServiceException e2 = assertThrows(ServiceException.class,
                () -> InvoiceValidator.validateApply(noAddress));
        assertTrue(e2.getMessage().contains("注册地址"), "实际：" + e2.getMessage());

        // 缺开户行
        AppInvoiceApplyBaseVO noBank = fullSpecialInvoice();
        noBank.setBankName(null);
        ServiceException e3 = assertThrows(ServiceException.class,
                () -> InvoiceValidator.validateApply(noBank));
        assertTrue(e3.getMessage().contains("开户行"), "实际：" + e3.getMessage());
    }

    @Test
    void testValidateApply_titleNameRequired_forBothTypes() {
        for (Integer type : new Integer[]{InvoiceTypeEnum.E_NORMAL.getType(), InvoiceTypeEnum.E_SPECIAL.getType()}) {
            AppInvoiceApplyBaseVO vo = fullSpecialInvoice();
            vo.setType(type);
            vo.setTitleName("   ");
            ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validateApply(vo),
                    "票种 " + type + " 缺名称也要拒");
            assertTrue(e.getMessage().contains("抬头名称"), "实际：" + e.getMessage());
        }
    }

    // ==================== 税号格式 ====================

    @Test
    void testValidateTaxNo_lengths() {
        // 15 / 18 / 20 位都合法（老税号 15、统一社会信用代码 18、部分 20）
        for (String taxNo : new String[]{"123456789012345", "91320104660690975E", "12345678901234567890"}) {
            AppInvoiceApplyBaseVO vo = fullSpecialInvoice();
            vo.setTaxNo(taxNo);
            assertDoesNotThrow(() -> InvoiceValidator.validateApply(vo), "税号 " + taxNo + " 应当合法");
        }
        // 位数不对、含非法字符
        for (String taxNo : new String[]{"12345678901234", "1234567890123456", "91320104660690975e", "9132010466069097-E"}) {
            AppInvoiceApplyBaseVO vo = fullSpecialInvoice();
            vo.setTaxNo(taxNo);
            ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validateApply(vo),
                    "税号 " + taxNo + " 应当被拒");
            assertEquals(INVOICE_TAX_NO_INVALID.getCode(), e.getCode());
        }
    }

    // ==================== 票种本身 ====================

    @Test
    void testValidateApply_typeInvalid_fail() {
        for (Integer type : new Integer[]{null, 0, 3, 99}) {
            AppInvoiceApplyBaseVO vo = fullSpecialInvoice();
            vo.setType(type);
            ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validateApply(vo));
            assertEquals(INVOICE_TYPE_INVALID.getCode(), e.getCode(), "票种 " + type + " 应当被拒");
        }
    }

    // ==================== 抬头簿：只要求名称 ====================

    @Test
    void testValidateTitle_onlyTitleName_pass() {
        AppInvoiceTitleSaveReqVO vo = new AppInvoiceTitleSaveReqVO();
        vo.setTitleName("张三");
        assertDoesNotThrow(() -> InvoiceValidator.validateTitle(vo));
    }

    @Test
    void testValidateTitle_titleNameRequired() {
        AppInvoiceTitleSaveReqVO vo = new AppInvoiceTitleSaveReqVO();
        vo.setTitleName(" ");
        ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validateTitle(vo));
        assertTrue(e.getMessage().contains("抬头名称"), "实际：" + e.getMessage());
    }

    @Test
    void testValidateTitle_taxNoFormatCheckedWhenPresent() {
        AppInvoiceTitleSaveReqVO vo = new AppInvoiceTitleSaveReqVO();
        vo.setTitleName("南京路桥机械有限公司");
        vo.setTaxNo("abc");
        ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validateTitle(vo));
        assertEquals(INVOICE_TAX_NO_INVALID.getCode(), e.getCode());
    }

}
