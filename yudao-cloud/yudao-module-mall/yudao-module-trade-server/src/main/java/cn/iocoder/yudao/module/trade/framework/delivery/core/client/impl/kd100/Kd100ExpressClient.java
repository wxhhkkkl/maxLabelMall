package cn.iocoder.yudao.module.trade.framework.delivery.core.client.impl.kd100;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.trade.framework.delivery.config.TradeExpressProperties;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.ExpressClient;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressMapQueryReqDTO;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressTrackQueryReqDTO;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressTrackRespDTO;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.kd100.Kd100ExpressMapReqDTO;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.kd100.Kd100ExpressMapRespDTO;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.kd100.Kd100ExpressQueryReqDTO;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.kd100.Kd100ExpressQueryRespDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.EXPRESS_API_QUERY_ERROR;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.EXPRESS_API_QUERY_FAILED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.EXPRESS_MAP_NOT_CONFIGURED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.EXPRESS_QUERY_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.trade.framework.delivery.core.client.convert.ExpressQueryConvert.INSTANCE;

/**
 * 快递 100 客户端
 *
 * @author jason
 */
@Slf4j
@AllArgsConstructor
public class Kd100ExpressClient implements ExpressClient {

    private static final String REAL_TIME_QUERY_URL = "https://poll.kuaidi100.com/poll/query.do";

    private static final String MAP_TRACK_URL = "https://poll.kuaidi100.com/poll/maptrack.do";

    private final RestTemplate restTemplate;
    private final TradeExpressProperties.Kd100Config config;

    /**
     * 查询快递轨迹
     *
     * @see <a href="https://api.kuaidi100.com/debug-tool/query/">接口文档</a>
     *
     * @param reqDTO 查询请求参数
     * @return 快递轨迹
     */
    @Override
    public List<ExpressTrackRespDTO> getExpressTrackList(ExpressTrackQueryReqDTO reqDTO) {
        // 发起请求
        Kd100ExpressQueryReqDTO requestDTO = INSTANCE.convert2(reqDTO)
                .setExpressCode(reqDTO.getExpressCode().toLowerCase());
        Kd100ExpressQueryRespDTO respDTO = httpRequest(REAL_TIME_QUERY_URL, requestDTO,
                Kd100ExpressQueryRespDTO.class);

        // 处理结果：三方原因只进日志，**不塞进给客户看的异常**（FR-259 / FR-274）
        if (Objects.equals("false", respDTO.getResult())) {
            log.warn("[getExpressTrackList][快递100 返回失败({}/{}）]",
                    respDTO.getReturnCode(), respDTO.getMessage());
            // 「编码无法识别」和「暂时失败」要给不同提示（FR-261）
            if (isUnsupportedCompany(respDTO)) {
                throw exception(EXPRESS_QUERY_NOT_SUPPORTED);
            }
            throw exception(EXPRESS_API_QUERY_FAILED);
        }
        if (CollUtil.isEmpty(respDTO.getTracks())) {
            return Collections.emptyList();
        }
        return INSTANCE.convertList2(respDTO.getTracks());
    }

    /**
     * 物流地图轨迹：返回快递100 的 trailUrl（可直接 iframe / 新窗口展示）
     */
    @Override
    public String getExpressMapUrl(ExpressMapQueryReqDTO reqDTO) {
        // 地图必须同时有「发货地址 + 地图模板」；缺失时明确报错，不猜地址
        if (StrUtil.isEmpty(reqDTO.getFrom()) || StrUtil.isEmpty(config.getMapConfigKey())) {
            throw exception(EXPRESS_MAP_NOT_CONFIGURED);
        }
        Kd100ExpressMapReqDTO request = new Kd100ExpressMapReqDTO()
                .setExpressCode(reqDTO.getExpressCode().toLowerCase())
                .setLogisticsNo(reqDTO.getLogisticsNo())
                .setPhone(reqDTO.getPhone())
                .setFrom(reqDTO.getFrom())
                .setTo(reqDTO.getTo())
                .setMapConfigKey(config.getMapConfigKey());
        Kd100ExpressMapRespDTO respDTO = httpRequest(MAP_TRACK_URL, request, Kd100ExpressMapRespDTO.class);
        if (Objects.equals("false", respDTO.getResult()) || StrUtil.isEmpty(respDTO.getTrailUrl())) {
            log.warn("[getExpressMapUrl][快递100 地图返回失败({}/{})]", respDTO.getReturnCode(), respDTO.getMessage());
            throw exception(EXPRESS_API_QUERY_FAILED);
        }
        return respDTO.getTrailUrl();
    }

    /**
     * 快递 100 API 请求
     *
     * @param url 请求 url
     * @param req 对应请求的请求参数
     * @param respClass 对应请求的响应 class
     * @param <Req> 每个请求的请求结构 Req DTO
     * @param <Resp> 每个请求的响应结构 Resp DTO
     */
    private <Req, Resp> Resp httpRequest(String url, Req req, Class<Resp> respClass) {
        // 请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        // 请求体
        String param = JsonUtils.toJsonString(req);
        String sign = generateReqSign(param, config.getKey(), config.getCustomer()); // 签名
        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("customer", config.getCustomer());
        requestBody.add("sign", sign);
        requestBody.add("param", param);
        // ⚠️ **不要打印 requestBody / param**：param 里含收件人手机号（FR-274）
        log.debug("[httpRequest][customer({}) 请求 {}]", config.getCustomer(), url);

        // 发送请求：超时 / 连接失败是 RestClientException，转成业务错误，
        // 让服务层能记下订单号并让前端走统一降级（FR-258 / FR-274 / SC-088）
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> responseEntity;
        try {
            responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        } catch (RestClientException ex) {
            log.warn("[httpRequest][请求快递100 失败({})]", ex.getMessage());
            throw exception(EXPRESS_API_QUERY_ERROR);
        }
        // ⚠️ 不打印完整响应体：它可能含手机号等收件信息（FR-274）
        log.debug("[httpRequest][响应状态({})]", responseEntity.getStatusCode());
        // 处理响应
        if (!responseEntity.getStatusCode().is2xxSuccessful()) {
            throw exception(EXPRESS_API_QUERY_ERROR);
        }
        // 空响应 / 非法 JSON 也要转成业务错误：否则 NPE / 解析异常会绕过服务层的订单号日志（FR-274 / SC-096）
        Resp result;
        try {
            result = JsonUtils.parseObject(responseEntity.getBody(), respClass);
        } catch (Exception ex) {
            log.warn("[httpRequest][响应解析失败({})]", ex.getMessage());
            throw exception(EXPRESS_API_QUERY_ERROR);
        }
        if (result == null) {
            log.warn("[httpRequest][响应为空]");
            throw exception(EXPRESS_API_QUERY_ERROR);
        }
        return result;
    }

    /**
     * 快递100 的「编码无法识别」判定
     *
     * 服务商对无效 {@code com} 返回 {@code returnCode=400} 且 message 点名编码/快递公司。
     * 其它 400（参数缺失等）仍按通用失败处理，避免把「单号格式错」误报成「暂不支持该快递公司」。
     */
    private boolean isUnsupportedCompany(Kd100ExpressQueryRespDTO respDTO) {
        if (!Objects.equals("400", respDTO.getReturnCode())) {
            return false;
        }
        String message = respDTO.getMessage();
        return message != null
                && (message.contains("编码") || message.contains("快递公司") || message.contains("不支持"));
    }

    private String generateReqSign(String param, String key, String customer) {
        String plainText = String.format("%s%s%s", param, key, customer);
        return HexUtil.encodeHexStr(DigestUtil.md5(plainText), false);
    }

}
