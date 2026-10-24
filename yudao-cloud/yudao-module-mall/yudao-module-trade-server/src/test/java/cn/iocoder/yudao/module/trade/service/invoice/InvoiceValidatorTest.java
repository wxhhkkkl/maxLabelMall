package cn.iocoder.yudao.module.trade.service.invoice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceApplyBaseVO;
import cn.iocoder.yudao.module.trade.enums.invoice.InvoiceTitleKindEnum;
import cn.iocoder.yudao.module.trade.enums.invoice.InvoiceTypeEnum;
import org.junit.jupiter.api.Test;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link InvoiceValidator} 的单元测试（T378 / Q27 / Q37 / Q38 / FR-155 / FR-156）。
 *
 * 这是**字段矩阵的可执行定义**，三条最要紧的断言：
 * · **企业**抬头缺税号必须被拒，且报错要**指明缺哪一项**（FR-156 要求客户知道去补什么）
 * · **个人**抬头只填名称必须通过（Q38：个人本来就没有纳税人识别号）
 * · **个人 + 专票**必须被拒（专票按税法只能开给企业）
 *
 * ⚠️ 个人抬头的税号是**选填**、不是**禁止** —— 自然人可以用身份证号当纳税人识别号。
 * 写成"个人不许填税号"会把合法场景挡在门外，所以这里专门有一条断言把它钉住。
 *
 * 纯函数测试：不需要 Spring、不需要 H2。
 */
class InvoiceValidatorTest {

    /** 企业抬头（Q38 之前唯一的形态，也是绝大多数既有用例的前提） */
    private AppInvoiceApplyBaseVO apply(Integer type, String titleName, String taxNo,
                                        String titleAddress, String titlePhone, String bankName) {
        return apply(type, InvoiceTitleKindEnum.COMPANY.getKind(), titleName, taxNo,
                titleAddress, titlePhone, bankName);
    }

    private AppInvoiceApplyBaseVO apply(Integer type, Integer titleKind, String titleName, String taxNo,
                                        String titleAddress, String titlePhone, String bankName) {
        AppInvoiceApplyBaseVO vo = new AppInvoiceApplyBaseVO();
        vo.setType(type);
        vo.setTitleKind(titleKind);
        vo.setTitleName(titleName);
        vo.setTaxNo(taxNo);
        vo.setTitleAddress(titleAddress);
        vo.setTitlePhone(titlePhone);
        vo.setBankName(bankName);
        return vo;
    }

    /** 个人 + 普票，只填了名称（Q38 要能存下来的那一张） */
    private AppInvoiceApplyBaseVO personalNormal(String titleName, String taxNo) {
        return apply(InvoiceTypeEnum.E_NORMAL.getType(), InvoiceTitleKindEnum.PERSONAL.getKind(),
                titleName, taxNo, null, null, null);
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
        // **企业**抬头：税号**两种票都必填**（原来普票是选填 —— 所有者要求统一）
        // 个人抬头是另一档，见下面「个人抬头」一节
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

    // ==================== 个人抬头（Q38） ====================

    @Test
    void testValidateApply_personal_onlyTitleName_pass() {
        // Q38 的全部意义所在：个人客户**没有纳税人识别号**，只填名称也要能存下来
        assertDoesNotThrow(() -> InvoiceValidator.validate(personalNormal("张三", null)));
    }

    @Test
    void testValidateApply_personal_taxNoOptional_notForbidden() {
        // 税号是**选填**：自然人可以用身份证号当纳税人识别号，
        // 所以"个人 + 合法税号"必须放行（写成禁止会把合法场景挡在门外）
        assertDoesNotThrow(() -> InvoiceValidator.validate(personalNormal("张三", "91320104660690975E")));
        // 但填了就要**合法** —— 选填不等于不校验
        ServiceException e = assertThrows(ServiceException.class,
                () -> InvoiceValidator.validate(personalNormal("张三", "123")));
        assertEquals(INVOICE_TAX_NO_INVALID.getCode(), e.getCode());
    }

    @Test
    void testValidateApply_personal_titleNameRequired() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> InvoiceValidator.validate(personalNormal("   ", null)));
        assertTrue(e.getMessage().contains("抬头名称"), "实际：" + e.getMessage());
    }

    @Test
    void testValidateApply_personal_withSpecial_fail() {
        // 增值税专用发票按税法只能开给企业 —— 收下来财务也开不出，必须在入口就拒
        AppInvoiceApplyBaseVO vo = fullSpecialInvoice();
        vo.setTitleKind(InvoiceTitleKindEnum.PERSONAL.getKind());
        ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validate(vo));
        assertEquals(INVOICE_TITLE_KIND_PERSONAL_SPECIAL.getCode(), e.getCode());
    }

    @Test
    void testValidateApply_titleKindInvalid_fail() {
        for (Integer kind : new Integer[]{null, 0, 3, 99}) {
            AppInvoiceApplyBaseVO vo = fullSpecialInvoice();
            vo.setTitleKind(kind);
            ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validate(vo),
                    "抬头类型 " + kind + " 应当被拒");
            assertEquals(INVOICE_TITLE_KIND_INVALID.getCode(), e.getCode());
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
        // 3 = 收据（Q61 新增），不在非法列表里
        for (Integer type : new Integer[]{null, 0, 4, 99}) {
            AppInvoiceApplyBaseVO vo = fullSpecialInvoice();
            vo.setType(type);
            ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validate(vo));
            assertEquals(INVOICE_TYPE_INVALID.getCode(), e.getCode(), "票种 " + type + " 应当被拒");
        }
    }

    // ==================== 收据（Q61）与邮箱 ====================

    @Test
    void testValidateApply_receipt_companyOnlyTitleName_pass() {
        // 收据不是税票：企业也只要求名称，**税号可以不填**
        assertDoesNotThrow(() -> InvoiceValidator.validate(
                apply(InvoiceTypeEnum.RECEIPT.getType(), "北京某某科技有限公司", null, null, null, null)));
    }

    @Test
    void testValidateApply_receipt_personalOnlyTitleName_pass() {
        // 个人 + 收据也允许（个人只被禁止开专票）
        assertDoesNotThrow(() -> InvoiceValidator.validate(
                apply(InvoiceTypeEnum.RECEIPT.getType(), InvoiceTitleKindEnum.PERSONAL.getKind(),
                        "张三", null, null, null, null)));
    }

    @Test
    void testValidateApply_receipt_titleNameRequired() {
        ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validate(
                apply(InvoiceTypeEnum.RECEIPT.getType(), "   ", null, null, null, null)));
        assertTrue(e.getMessage().contains("抬头名称"), "实际：" + e.getMessage());
    }

    @Test
    void testValidateApply_email_optionalAndTrimmedAndChecked() {
        AppInvoiceApplyBaseVO vo = apply(InvoiceTypeEnum.E_NORMAL.getType(), "南京路桥机械有限公司",
                "91320104660690975E", null, null, null);
        // 不填：通过
        assertDoesNotThrow(() -> InvoiceValidator.validate(vo));
        // 填了：trim 后保留；格式合法通过
        vo.setEmail("  finance@example.com  ");
        assertDoesNotThrow(() -> InvoiceValidator.validate(vo));
        assertEquals("finance@example.com", vo.getEmail());
        // 填了但不像邮箱：拒
        vo.setEmail("not-an-email");
        ServiceException e = assertThrows(ServiceException.class, () -> InvoiceValidator.validate(vo));
        assertEquals(INVOICE_EMAIL_INVALID.getCode(), e.getCode());
    }

    // ==================== 抬头簿（Q36 之后与申请**同一套规则**） ====================

    // 抬头带上票种与抬头类型之后，"抬头簿保存"与"开票申请"的校验不再有区别，
    // 所以上面那两张矩阵**同时就是抬头簿的规则** —— 不再单独写一份（两份必然分叉）。


}
