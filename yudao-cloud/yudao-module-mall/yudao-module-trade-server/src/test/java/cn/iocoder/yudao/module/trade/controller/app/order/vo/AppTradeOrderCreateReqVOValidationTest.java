package cn.iocoder.yudao.module.trade.controller.app.order.vo;

import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `AppTradeOrderCreateReqVO` 上**买家留言长度**那条约束的单测。
 *
 * 纯 Bean Validation，**不起 Spring**（用框架里的 {@link ValidationUtils}）——
 * 本模块没有 MockMvc 那套基建，为一条 `@Size` 搭一套不划算。
 *
 * ⚠️ 这条约束不是可有可无的：`trade_order.user_remark` 是 **varchar(200)**，
 * 而 MySQL 非严格模式下超长会**静默截断** —— 客户写的字被砍掉一截，
 * 而系统里没有任何痕迹（与发票那边卡列宽同一个理由）。
 */
public class AppTradeOrderCreateReqVOValidationTest {

    /**
     * 组一个**除了留言之外全部合法**的请求。
     *
     * 其余必填项都要填上：少填一个，报出来的就是别的约束 ——
     * 那样这几条用例会变成"看起来测了、其实没测到留言长度"。
     */
    private AppTradeOrderCreateReqVO buildReqVO(String remark) {
        AppTradeOrderCreateReqVO reqVO = new AppTradeOrderCreateReqVO();
        AppTradeOrderSettlementReqVO.Item item = new AppTradeOrderSettlementReqVO.Item();
        item.setSkuId(2048L);
        item.setCount(1);
        reqVO.setItems(List.of(item));
        reqVO.setPointStatus(false);
        reqVO.setDeliveryType(DeliveryTypeEnum.EXPRESS.getType());
        reqVO.setRemark(remark);
        return reqVO;
    }

    @Test
    public void testRemark_正好200字_通过() {
        assertDoesNotThrow(() -> ValidationUtils.validate(buildReqVO("留".repeat(200))));
    }

    @Test
    public void testRemark_201字_被拒且点名是买家留言() {
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class,
                () -> ValidationUtils.validate(buildReqVO("留".repeat(201))));
        assertTrue(e.getMessage().contains("买家留言"), "实际：" + e.getMessage());
    }

    /** 留言是**选填** —— 别把"没留言"也拦下来 */
    @Test
    public void testRemark_不填或空串_通过() {
        assertDoesNotThrow(() -> ValidationUtils.validate(buildReqVO(null)));
        assertDoesNotThrow(() -> ValidationUtils.validate(buildReqVO("")));
    }

}
