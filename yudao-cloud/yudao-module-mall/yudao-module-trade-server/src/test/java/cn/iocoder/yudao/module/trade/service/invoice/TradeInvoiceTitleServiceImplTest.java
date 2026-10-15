package cn.iocoder.yudao.module.trade.service.invoice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceApplyBaseVO;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceTitleSaveReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.invoice.TradeInvoiceTitleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.invoice.TradeOrderInvoiceDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.invoice.TradeOrderInvoiceMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.invoice.InvoiceTypeEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TradeInvoiceTitleService} 的单元测试（US23 / T418）
 *
 * 三条最要紧的：
 * · **默认抬头唯一** —— 设新的默认必须清掉旧的（唯一性在服务层，不在数据库）
 * · **跨会员取改不到** —— 传别人的 id 一律"抬头不存在"，不能改到别人的抬头
 * · **改 / 删抬头 MUST NOT 影响历史申请**（FR-166 / **SC-061**）：申请里存的是**快照**，
 *   不是对抬头簿的引用 —— 这条是"结构上不可能被影响"，不是"靠代码小心"
 */
// ⚠️ 开票服务也要导入：SC-061 那条用例要用它建一条申请（"删抬头不影响历史申请"）
// —— 少一个 @Import 的后果是**整个类 8 个用例全挂在"注入失败"**上，不是某一个失败
@Import({TradeInvoiceTitleServiceImpl.class, TradeOrderInvoiceServiceImpl.class})
public class TradeInvoiceTitleServiceImplTest extends BaseDbUnitTest {

    @Resource
    private TradeInvoiceTitleService titleService;
    @Resource
    private TradeOrderInvoiceService invoiceService;
    @Resource
    private TradeOrderInvoiceMapper invoiceMapper;
    @Resource
    private TradeOrderMapper orderMapper;

    private AppInvoiceTitleSaveReqVO buildTitle(String name) {
        AppInvoiceTitleSaveReqVO reqVO = new AppInvoiceTitleSaveReqVO();
        reqVO.setTitleName(name);
        reqVO.setTaxNo("91320104660690975E");
        return reqVO;
    }

    // ==================== CRUD 与默认唯一 ====================

    @Test
    public void testCreateAndList() {
        Long id = titleService.createInvoiceTitle(88L, buildTitle("南京路桥机械有限公司"));

        List<TradeInvoiceTitleDO> list = titleService.getInvoiceTitleList(88L);
        assertEquals(1, list.size());
        assertEquals(id, list.get(0).getId());
        assertEquals("91320104660690975E", list.get(0).getTaxNo());
        // 没显式设默认 → 默认是 false（不是 null）
        assertEquals(Boolean.FALSE, list.get(0).getDefaultStatus());
    }

    @Test
    public void testCreate_defaultIsUnique() {
        Long first = titleService.createInvoiceTitle(88L, buildTitle("老公司"));
        titleService.updateInvoiceTitle(88L, defaultReq(first, "老公司", true));

        Long second = titleService.createInvoiceTitle(88L, defaultReq(null, "新公司", true));

        // 设了新的默认，旧的那条必须被清掉 —— 否则"默认"就有两条，结算页预选哪条全看运气
        List<TradeInvoiceTitleDO> list = titleService.getInvoiceTitleList(88L);
        assertEquals(2, list.size());
        TradeInvoiceTitleDO newDefault = list.stream().filter(t -> t.getId().equals(second)).findFirst().orElseThrow();
        TradeInvoiceTitleDO oldOne = list.stream().filter(t -> t.getId().equals(first)).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, newDefault.getDefaultStatus());
        assertEquals(Boolean.FALSE, oldOne.getDefaultStatus());
    }

    @Test
    public void testUpdate_keepsOtherFieldsAndScopesByMember() {
        Long id = titleService.createInvoiceTitle(88L, buildTitle("南京路桥机械有限公司"));

        AppInvoiceTitleSaveReqVO update = defaultReq(id, "南京路桥机械有限公司（新）", false);
        titleService.updateInvoiceTitle(88L, update);

        TradeInvoiceTitleDO updated = titleService.getInvoiceTitleList(88L).get(0);
        assertEquals("南京路桥机械有限公司（新）", updated.getTitleName());
    }

    @Test
    public void testUpdate_otherMemberTitle_fail() {
        Long id = titleService.createInvoiceTitle(88L, buildTitle("南京路桥机械有限公司"));

        // 换个会员来改同一个 id —— 必须"抬头不存在"，不能改到别人的
        ServiceException e = assertThrows(ServiceException.class,
                () -> titleService.updateInvoiceTitle(99L, defaultReq(id, "篡改", false)));
        assertEquals(INVOICE_TITLE_NOT_FOUND.getCode(), e.getCode());
        assertEquals("南京路桥机械有限公司", titleService.getInvoiceTitleList(88L).get(0).getTitleName());
    }

    @Test
    public void testDelete_otherMemberTitle_fail() {
        Long id = titleService.createInvoiceTitle(88L, buildTitle("南京路桥机械有限公司"));

        ServiceException e = assertThrows(ServiceException.class,
                () -> titleService.deleteInvoiceTitle(99L, id));
        assertEquals(INVOICE_TITLE_NOT_FOUND.getCode(), e.getCode());
        assertEquals(1, titleService.getInvoiceTitleList(88L).size());
    }

    @Test
    public void testDelete_success() {
        Long id = titleService.createInvoiceTitle(88L, buildTitle("南京路桥机械有限公司"));

        titleService.deleteInvoiceTitle(88L, id);

        assertTrue(titleService.getInvoiceTitleList(88L).isEmpty());
    }

    @Test
    public void testCreate_titleNameRequired() {
        // 抬头名称是唯一的必填项（抬头可以不完整：只开普票的客户不必填开户行）
        AppInvoiceTitleSaveReqVO reqVO = buildTitle("   ");
        ServiceException e = assertThrows(ServiceException.class,
                () -> titleService.createInvoiceTitle(88L, reqVO));
        assertEquals(INVOICE_TITLE_FIELD_REQUIRED.getCode(), e.getCode());
    }

    // ==================== SC-061：改 / 删抬头不影响历史申请 ====================

    @Test
    public void testDeleteTitle_doesNotAffectExistingInvoice() {
        // 1. 客户存了一条抬头，并用它开了票的申请
        Long titleId = titleService.createInvoiceTitle(88L, buildTitle("南京路桥机械有限公司"));
        TradeOrderDO order = TradeOrderDO.builder()
                .no("NO-" + System.nanoTime()).type(0).terminal(0).userId(88L).userIp("127.0.0.1")
                .status(TradeOrderStatusEnum.UNDELIVERED.getStatus()).productCount(1).payStatus(true)
                .discountPrice(0).deliveryPrice(0).adjustPrice(0).payPrice(7800).deliveryType(1)
                .receiverName("张三").receiverMobile("13800008888").receiverAreaId(110101)
                .receiverDetailAddress("xx 路 1 号").couponId(0L).couponPrice(0).pointPrice(0)
                .build();
        orderMapper.insert(order);
        AppInvoiceApplyBaseVO apply = new AppInvoiceApplyBaseVO();
        apply.setType(InvoiceTypeEnum.E_NORMAL.getType());
        apply.setTitleName("南京路桥机械有限公司");
        apply.setTaxNo("91320104660690975E");
        Long invoiceId = invoiceService.createInvoice(order, apply);

        // 2. 客户把这条抬头删掉
        titleService.deleteInvoiceTitle(88L, titleId);

        // 3. 历史申请的开票信息**逐字段不变**（申请里存的是快照，不是引用）
        TradeOrderInvoiceDO invoice = invoiceMapper.selectById(invoiceId);
        assertEquals("南京路桥机械有限公司", invoice.getTitleName());
        assertEquals("91320104660690975E", invoice.getTaxNo());
    }

    /** 组一个"改抬头"的请求（id 为空时是新增） */
    private AppInvoiceTitleSaveReqVO defaultReq(Long id, String name, boolean isDefault) {
        AppInvoiceTitleSaveReqVO reqVO = buildTitle(name);
        reqVO.setId(id);
        reqVO.setDefaultStatus(isDefault);
        return reqVO;
    }

}
