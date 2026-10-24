package cn.iocoder.yudao.module.trade.framework.delivery.core.client;

import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressMapQueryReqDTO;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressTrackQueryReqDTO;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressTrackRespDTO;

import java.util.List;

/**
 * 快递客户端接口
 *
 * @author jason
 */
public interface ExpressClient {

    /**
     * 快递实时查询
     *
     * @param reqDTO 查询请求参数
     */
    // TODO @jason：返回字段可以参考 https://doc.youzanyun.com/detail/API/0/5 响应的 data
    List<ExpressTrackRespDTO> getExpressTrackList(ExpressTrackQueryReqDTO reqDTO);

    /**
     * 物流地图轨迹（返回可直接展示的 trailUrl）
     *
     * 默认不支持；目前只有快递100 实现。返回 null / 空串时前端提示「无法生成地图」。
     */
    default String getExpressMapUrl(ExpressMapQueryReqDTO reqDTO) {
        return null;
    }

}
