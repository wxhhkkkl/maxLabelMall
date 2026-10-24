package cn.iocoder.yudao.module.trade.controller.admin.delivery.vo.express;

import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `DeliveryExpressBaseVO.kd100Code` 的长度约束（010 / T040）。
 *
 * DB 是 `varchar(64)`；线上 sql_mode 含 STRICT_TRANS_TABLES，超长直接报
 * `Data too long`，会被兜底成 500「系统异常」。所以在 VO 上先拦一道，
 * 与 016（member_address.name）同一类教训。
 */
public class DeliveryExpressCreateReqVOValidationTest {

    private DeliveryExpressCreateReqVO build(String kd100Code) {
        DeliveryExpressCreateReqVO vo = new DeliveryExpressCreateReqVO();
        vo.setCode("SF");
        vo.setName("顺丰速运");
        vo.setSort(1);
        vo.setStatus(0);
        vo.setKd100Code(kd100Code);
        return vo;
    }

    @Test
    public void testKd100Code_64_pass() {
        assertDoesNotThrow(() -> ValidationUtils.validate(build("a".repeat(64))));
    }

    @Test
    public void testKd100Code_65_rejected() {
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class,
                () -> ValidationUtils.validate(build("a".repeat(65))));
        assertTrue(e.getMessage().contains("快递100 编码"), "实际：" + e.getMessage());
    }

    @Test
    public void testKd100Code_null_pass() {
        // 选填：为空是合法状态（安能这类暂不支持查询的快递公司）
        assertDoesNotThrow(() -> ValidationUtils.validate(build(null)));
        assertDoesNotThrow(() -> ValidationUtils.validate(build("")));
    }

}
