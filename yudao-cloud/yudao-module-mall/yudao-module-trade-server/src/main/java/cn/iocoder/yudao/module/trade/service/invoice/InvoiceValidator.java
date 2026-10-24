package cn.iocoder.yudao.module.trade.service.invoice;

import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceApplyBaseVO;
import cn.iocoder.yudao.module.trade.enums.invoice.InvoiceTitleKindEnum;
import cn.iocoder.yudao.module.trade.enums.invoice.InvoiceTypeEnum;

import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;

/**
 * 开票信息的校验（**全仓唯一一处**，T383）
 *
 * 三个入口共用它：下单时的 `invoice` 子对象、订单详情补开、发票抬头簿保存
 * （Q36 之后抬头也带票种，前两处的"按票种判"直接就是第三处的规则）。
 * 之所以要收在一处：**票种字段矩阵是财务口径**（Q27），散成两份必然在改票种时漏改一处。
 *
 * ⚠️ 本类**会把字段 trim 后写回入参 VO** —— 这是有意的：spec 与 data-model 都要求
 * "提交时 trim"，而"校验用的值"与"落库的值"只要是同一份，就不可能出现
 * "校验时看到的是空的、落库的是三个空格"这种分叉。调用方随后直接从 VO 取值落库即可。
 *
 * @author 芋道源码
 */
public final class InvoiceValidator {

    private InvoiceValidator() {
    }

    /**
     * 纳税人识别号：15 位（老税号）、18 位（统一社会信用代码）、20 位都合法。
     *
     * ⚠️ **只校验长度与字符集，不校验校验位**：校验位算法（GB 32100）会把"格式对但校验位错"的
     * 客户挡在门外，而他手上那个号确实就是那样写的 —— 那是开票平台该报的错，不是我们的。
     * 大小写：只接受**大写**字母（客户手打小写会被拒，报错信息里说明了格式）。
     */
    private static final Pattern TAX_NO_PATTERN = Pattern.compile("^[0-9A-Z]{15}$|^[0-9A-Z]{18}$|^[0-9A-Z]{20}$");

    /** 邮箱（选填；填了就要像邮箱）—— 只做轻量格式判断，不做存在性验证 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** 列宽上限，与 `007-order-invoice.sql` 和 data-model §4 逐字一致 */
    private static final int TITLE_NAME_MAX = 255;
    private static final int TAX_NO_MAX = 64;
    private static final int TITLE_ADDRESS_MAX = 255;
    private static final int TITLE_PHONE_MAX = 64;
    private static final int BANK_NAME_MAX = 255;
    private static final int EMAIL_MAX = 128;

    /**
     * 校验一次**开票信息**（下单 / 补开 / 抬头簿保存三处共用）
     *
     * **必填随「抬头类型 + 票种」而变**（Q37 / Q38 / Q61）：
     * · **企业 + 普票/专票** → 抬头名称 + 纳税人识别号
     * · **个人 + 普票** → **只要抬头名称**
     * · **收据（企业/个人）** → **只要抬头名称**（不是税票，不要求税号）
     * 地址 / 电话 / 开户行及账号 / 邮箱一律**选填**，填了要合法。缺必填项时在报错里**点名是哪一项**（FR-156）。
     *
     * ⚠️ 个人抬头的税号是**选填、不是禁止** —— 自然人可以用身份证号当纳税人识别号，
     * 写成"个人不许填"会把合法场景挡在门外。
     *
     * ⚠️ **Q36 之后抬头也带票种与抬头类型**，所以"抬头簿保存"与"开票申请"不再有区别 ——
     * 原来那个更宽松的 `validateTitle`（只要求名称）已经删掉：两份规则必然分叉。
     */
    public static void validate(AppInvoiceApplyBaseVO invoice) {
        // 票种本身：null / 0 / 99 一律拒（别让它悄悄落库成"未知票种"）
        InvoiceTypeEnum type = InvoiceTypeEnum.valueOf(invoice.getType());
        if (type == null) {
            throw exception(INVOICE_TYPE_INVALID);
        }
        // 抬头类型同理
        InvoiceTitleKindEnum titleKind = InvoiceTitleKindEnum.valueOf(invoice.getTitleKind());
        if (titleKind == null) {
            throw exception(INVOICE_TITLE_KIND_INVALID);
        }
        // **个人只能开普票**：增值税专用发票按税法只能开给一般纳税人企业，
        // 收下来财务也开不出 —— 在入口就拒，别让后台多出一条办不成的申请
        if (titleKind == InvoiceTitleKindEnum.PERSONAL && type == InvoiceTypeEnum.E_SPECIAL) {
            throw exception(INVOICE_TITLE_KIND_PERSONAL_SPECIAL);
        }
        // 先 trim，再校验 —— 校验看到的值与落库的值是同一份
        invoice.setTitleName(trim(invoice.getTitleName()));
        invoice.setTaxNo(trim(invoice.getTaxNo()));
        invoice.setTitleAddress(trim(invoice.getTitleAddress()));
        invoice.setTitlePhone(trim(invoice.getTitlePhone()));
        invoice.setBankName(trim(invoice.getBankName()));
        invoice.setEmail(trim(invoice.getEmail()));

        // 必填矩阵：地址 / 电话 / 开户行一律**选填**，专票也一样（客户手边未必有开户行账号，
        // 不该因此存不下抬头、或者提交不了开票申请；那几项财务开票时会核对）
        requireText(invoice.getTitleName(), "抬头名称");
        // 收据不是税票：**企业/个人都只要名称**（Q61）；普票 / 专票的企业才要求税号
        if (titleKind == InvoiceTitleKindEnum.COMPANY && type != InvoiceTypeEnum.RECEIPT) {
            requireText(invoice.getTaxNo(), "纳税人识别号");
        }
        checkFormats(invoice.getTitleName(), invoice.getTaxNo(),
                invoice.getTitleAddress(), invoice.getTitlePhone(), invoice.getBankName(), invoice.getEmail());
    }

    // ==================== 内部 ====================

    /** 选填字段"填了就要合法"：税号格式 + 各列长度上限 */
    private static void checkFormats(String titleName, String taxNo, String titleAddress,
                                     String titlePhone, String bankName, String email) {
        checkLength("抬头名称", titleName, TITLE_NAME_MAX);
        if (!isBlank(taxNo) && !TAX_NO_PATTERN.matcher(taxNo).matches()) {
            throw exception(INVOICE_TAX_NO_INVALID);
        }
        checkLength("纳税人识别号", taxNo, TAX_NO_MAX);
        checkLength("注册地址", titleAddress, TITLE_ADDRESS_MAX);
        checkLength("注册电话", titlePhone, TITLE_PHONE_MAX);
        checkLength("开户行及账号", bankName, BANK_NAME_MAX);
        if (!isBlank(email) && !EMAIL_PATTERN.matcher(email).matches()) {
            throw exception(INVOICE_EMAIL_INVALID);
        }
        checkLength("邮箱", email, EMAIL_MAX);
    }

    /** 必填：trim 后为空即视为没填（报错点名是哪一项） */
    private static void requireText(String value, String fieldName) {
        if (isBlank(value)) {
            throw exception(INVOICE_TITLE_FIELD_REQUIRED, fieldName);
        }
    }

    /**
     * 长度上限 —— **不能省**：MySQL 在非严格模式下会**静默截断**超长值，
     * 于是客户填的公司名被砍掉一截、开出来的票抬头是错的，而系统里没有任何痕迹
     */
    private static void checkLength(String fieldName, String value, int max) {
        if (value != null && value.length() > max) {
            throw exception(INVOICE_TITLE_FIELD_REQUIRED, fieldName + " 过长（最多 " + max + " 个字符）");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isEmpty(); // 已 trim，无需再看空白
    }

    /**
     * trim，并把**空串归一成 null**。
     *
     * 为什么要归一：前端提交普票时会带上 `"titleAddress": ""` 这类空字段（契约里就是这么写的），
     * 若原样落库就会得到一堆 `''` —— 于是"没填"在数据里有了两种表示（`''` 与 `NULL`），
     * 后台列表与排查时都得多判一次。统一成 NULL，`''` 这种值在库里就不存在。
     */
    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
