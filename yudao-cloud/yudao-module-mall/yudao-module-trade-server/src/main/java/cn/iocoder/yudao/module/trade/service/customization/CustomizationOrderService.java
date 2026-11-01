package cn.iocoder.yudao.module.trade.service.customization;

import cn.iocoder.yudao.module.trade.controller.app.customization.vo.AppCustomizationOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationOrderDO;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationOrderCreateResultBO;

import java.util.Collection;
import java.util.List;

/**
 * 定制订单 Service（014）
 */
public interface CustomizationOrderService {

    /**
     * 创建定制订单
     *
     * @return changed = true 时表示价格已变、未建单（Q101）
     */
    CustomizationOrderCreateResultBO createOrder(Long userId, AppCustomizationOrderCreateReqVO reqVO);

    /** 按订单编号查定制扩展 */
    CustomizationOrderDO getByOrderId(Long orderId);

    /** 批量查（订单列表用） */
    List<CustomizationOrderDO> getListByOrderIds(Collection<Long> orderIds);

}
