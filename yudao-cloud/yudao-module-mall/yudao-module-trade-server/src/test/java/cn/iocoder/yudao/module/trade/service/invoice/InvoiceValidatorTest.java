package cn.iocoder.yudao.module.trade.service.invoice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceApplyBaseVO;
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
    void testValidateApply_normal_titleNameAndTaxNo_pass() {
        // 2026-10-15 所有者口径：**两种票都只必填「名称 + 税号」**，其余一律选填
        assertDoesNotThrow(() -> InvoiceValidator.validate(
                apply(InvoiceTypeEnum.E_NORMAL.getType(), "南京路桥机械有限公司", "91320104660690975E",
                        null, null, null)));
    }

    @Test
    void testValidateApply_taxNoRequired_forBothTypes() {
        // 税号**两种票都必填**（原来普票是选填 —— 所有者要求统一）
        for (Integer type : new Integer[]{InvoiceTypeEnum.E_NORMAL.getType(), InvoiceTypeEnum.E_SPECIAL.getType()}) {
            ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validate(
                    apply(type, "南京路桥机械有限公司", null, null, null, null)), "票种 " + type + " 缺税号要拒");
            assertTrue(e.getMessage().contains("纳税人识别号"), "实际：" + e.getMessage());
        }
    }

    @Test
    void testValidateApply_taxNoFormatStillChecked() {
        ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validate(
                apply(InvoiceTypeEnum.E_NORMAL.getType(), "南京路桥机械有限公司", "123", null, null, null)));
        assertEquals(INVOICE_TAX_NO_INVALID.getCode(), e.getCode());
    }

    // ==================== 专票：五项全要 ====================

    @Test
    void testValidateApply_special_allFields_pass() {
        assertDoesNotThrow(() -> InvoiceValidator.validate(fullSpecialInvoice()));
    }

    @Test
    void testValidateApply_special_addressPhoneBank_optional() {
        // **专票的地址 / 电话 / 开户行**（2026-10-15 起）是**选填** ——
        // 客户手边未必有开户行账号，不该因此存不下/开不了票
        assertDoesNotThrow(() -> InvoiceValidator.validate(
                apply(InvoiceTypeEnum.E_SPECIAL.getType(), "南京路桥机械有限公司", "91320104660690975E",
                        null, null, null)));
        assertDoesNotThrow(() -> InvoiceValidator.validate(
                apply(InvoiceTypeEnum.E_SPECIAL.getType(), "南京路桥机械有限公司", "91320104660690975E",
                        "  ", "  ", "  ")));
    }

    @Test
    void testValidateApply_titleNameRequired_forBothTypes() {
        for (Integer type : new Integer[]{InvoiceTypeEnum.E_NORMAL.getType(), InvoiceTypeEnum.E_SPECIAL.getType()}) {
            AppInvoiceApplyBaseVO vo = fullSpecialInvoice();
            vo.setType(type);
            vo.setTitleName("   ");
            ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validate(vo),
                    "票种 " + type + " 缺名称也要拒（名称两种票都必填）");
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
            assertDoesNotThrow(() -> InvoiceValidator.validate(vo), "税号 " + taxNo + " 应当合法");
        }
        // 位数不对、含非法字符
        for (String taxNo : new String[]{"12345678901234", "1234567890123456", "91320104660690975e", "9132010466069097-E"}) {
            AppInvoiceApplyBaseVO vo = fullSpecialInvoice();
            vo.setTaxNo(taxNo);
            ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validate(vo),
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
            ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validate(vo));
            assertEquals(INVOICE_TYPE_INVALID.getCode(), e.getCode(), "票种 " + type + " 应当被拒");
        }
    }

    // ==================== 抬头簿（Q36 之后与申请**同一套规则**） ====================

    // 抬头带上票种之后，"抬头簿保存"与"开票申请"的校验不再有区别（都按票种判必填），
    // 所以上面那张票种矩阵**同时就是抬头簿的规则** —— 不再单独写一份（两份必然分叉）。


}
