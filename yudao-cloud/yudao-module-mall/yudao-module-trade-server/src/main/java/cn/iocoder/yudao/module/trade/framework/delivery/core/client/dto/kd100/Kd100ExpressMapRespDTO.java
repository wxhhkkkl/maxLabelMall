package cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.kd100;

import lombok.Data;

/**
 * 快递100 地图轨迹查询 Resp DTO
 */
@Data
public class Kd100ExpressMapRespDTO {

    /**
     * 失败时返回 "false"
     */
    private String result;

    /**
     * 快递100 返回码（400 = 参数/地址错误）
     */
    private String returnCode;

    private String message;

    /**
     * 可直接展示的地图轨迹地址（iframe / 新窗口）
     */
    private String trailUrl;

}
