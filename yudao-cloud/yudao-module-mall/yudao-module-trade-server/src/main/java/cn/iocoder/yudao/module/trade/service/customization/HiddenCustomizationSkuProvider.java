package cn.iocoder.yudao.module.trade.service.customization;

import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

import static cn.iocoder.yudao.module.trade.enums.customization.CustomizationConstants.HIDDEN_SKU_ID;
import static cn.iocoder.yudao.module.trade.enums.customization.CustomizationConstants.HIDDEN_SPU_ID;

/**
 * 隐藏的「印刷定制服务」SKU / SPU 提供者（014 / R88）
 *
 * 由 `sql/mysql/027-print-customization.sql` 预置；缺失时说明迁移没执行，直接失败比静默扣错库存好。
 */
@Component
public class HiddenCustomizationSkuProvider {

    @Resource
    private ProductSkuApi productSkuApi;
    @Resource
    private ProductSpuApi productSpuApi;

    public ProductSkuRespDTO getSku() {
        List<ProductSkuRespDTO> list = productSkuApi.getSkuList(List.of(HIDDEN_SKU_ID)).getCheckedData();
        if (list == null || list.isEmpty()) {
            throw new IllegalStateException("隐藏的定制 SKU(" + HIDDEN_SKU_ID + ") 不存在，请先执行 027 迁移");
        }
        return list.get(0);
    }

    public ProductSpuRespDTO getSpu() {
        ProductSpuRespDTO spu = productSpuApi.getSpu(HIDDEN_SPU_ID).getCheckedData();
        if (spu == null) {
            throw new IllegalStateException("隐藏的定制 SPU(" + HIDDEN_SPU_ID + ") 不存在，请先执行 027 迁移");
        }
        return spu;
    }

}
