package cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.kd100;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 快递100 地图轨迹查询 Req DTO（maptrack.do 的 param）
 *
 * @see <a href="https://api.kuaidi100.com/document/5ff2c3e7ba1bf00302f5612e">快递查询地图轨迹</a>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Kd100ExpressMapReqDTO {

    @JsonProperty("com")
    private String expressCode;

    @JsonProperty("num")
    private String logisticsNo;

    private String phone;

    /**
     * 发货地址（省市区）
     */
    private String from;

    /**
     * 收件地址（省市区）
     */
    private String to;

    /**
     * 返回高级状态与路线规划：7
     */
    @JsonProperty("resultv2")
    private String resultV2 = "7";

    /**
     * 0 = JSON
     */
    private String show = "0";

    /**
     * 快递100 地图模板 id
     */
    @JsonProperty("mapConfigKey")
    private String mapConfigKey;

    /**
     * 轨迹顺序：desc
     */
    private String order = "desc";

}
