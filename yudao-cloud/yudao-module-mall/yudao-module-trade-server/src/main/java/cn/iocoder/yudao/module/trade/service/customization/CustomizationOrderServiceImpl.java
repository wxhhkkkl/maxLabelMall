package cn.iocoder.yudao.module.trade.service.customization;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.trade.controller.app.customization.vo.AppCustomizationOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customization.CustomizationOrderMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationOrderCreateResultBO;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationQuoteBO;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationSpecBO;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMIZATION_ORDER_SUBMIT_TOO_FAST;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMIZATION_SPEC_OUT_OF_RANGE;
import static cn.iocoder.yudao.module.trade.enums.customization.CustomizationConstants.HIDDEN_SKU_ID;
import static cn.iocoder.yudao.module.trade.enums.customization.CustomizationConstants.SUBMIT_LOCK_KEY_PREFIX;
import static cn.iocoder.yudao.module.trade.enums.customization.CustomizationConstants.SUBMIT_LOCK_SECONDS;

/**
 * 定制订单实现（014 / R89~R95）
 */
@Slf4j
@Service
public class CustomizationOrderServiceImpl implements CustomizationOrderService {

    private static final String LOCK_PENDING = "PENDING";

    @Resource
    private CustomizationPriceService customizationPriceService;
    @Resource
    private CustomizationSpecValidator customizationSpecValidator;
    @Resource
    private TradeOrderUpdateService tradeOrderUpdateService;
    @Resource
    private CustomizationOrderMapper customizationOrderMapper;
    @Resource
    private TradeOrderMapper tradeOrderMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomizationOrderCreateResultBO createOrder(Long userId, AppCustomizationOrderCreateReqVO reqVO) {
        CustomizationSpecBO spec = CustomizationSpecConvert.toSpec(reqVO);
        // 文件只在建单时校验（报价阶段允许还没上传）
        customizationSpecValidator.validateFiles(spec.getFiles());
        // 四步向导的成品形态与排版在建单时必填（Q109）
        if (spec.getForm() == null || spec.getRowCount() == null || spec.getColumnCount() == null) {
            throw exception(CUSTOMIZATION_SPEC_OUT_OF_RANGE);
        }

        // 1. 报价：与提交价不一致 → 不建单，返回新报价（Q101）
        CustomizationQuoteBO quote = customizationPriceService.quote(spec, userId, reqVO.getAddressId(), reqVO.getDeliveryType());
        CustomizationOrderCreateResultBO result = new CustomizationOrderCreateResultBO();
        if (!quote.getPayable().equals(reqVO.getExpectedAmount())) {
            result.setChanged(true);
            result.setQuote(quote);
            return result;
        }

        // 2. 60 秒幂等（Q99）：同一客户 + 同一配置只生成一笔
        String key = SUBMIT_LOCK_KEY_PREFIX + userId + ":" + specHash(spec);
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, LOCK_PENDING, Duration.ofSeconds(SUBMIT_LOCK_SECONDS));
        if (!Boolean.TRUE.equals(locked)) {
            CustomizationOrderCreateResultBO duplicated = readDuplicated(key);
            if (duplicated != null) {
                return duplicated;
            }
            throw exception(CUSTOMIZATION_ORDER_SUBMIT_TOO_FAST);
        }

        try {
            // 3. 走既有建单链路：订单项指向隐藏 SKU，价格由 CustomizationPriceService 重算
            AppTradeOrderCreateReqVO createReqVO = buildTradeOrderReq(reqVO);
            TradeOrderDO order = tradeOrderUpdateService.createOrder(userId, createReqVO);

            // 4. 落定制要求（同一事务）
            CustomizationOrderDO row = CustomizationOrderDO.builder()
                    .orderId(order.getId())
                    .materialId(quote.getMaterialId())
                    .materialName(quote.getMaterialName())
                    .width(spec.getWidth()).height(spec.getHeight()).quantity(spec.getQuantity())
                    .labelCount(spec.getLabelCount() != null ? spec.getLabelCount() : spec.getQuantity())
                    .templateName(spec.getTemplateName())
                    .unitArea(quote.getUnitArea()).totalArea(quote.getTotalArea())
                    .mode(spec.getMode())
                    .form(spec.getForm())
                    .sheetSpec(spec.getSheetSpec())
                    .finishing(spec.getFinishing())
                    .tearLine(Boolean.TRUE.equals(spec.getTearLine()))
                    .rowCount(spec.getRowCount()).columnCount(spec.getColumnCount())
                    .rowGap(spec.getRowGap()).columnGap(spec.getColumnGap())
                    .axisDiameter(spec.getAxisDiameter())
                    .dieCut(Boolean.TRUE.equals(spec.getDieCut()))
                    .urgent(Boolean.TRUE.equals(spec.getUrgent()))
                    .remark(spec.getRemark())
                    .priceBreakdown(JsonUtils.toJsonString(quote.getBreakdown()))
                    .paramSnapshot(quote.getParamSnapshot())
                    .files(JsonUtils.toJsonString(spec.getFiles()))
                    .build();
            customizationOrderMapper.insert(row);

            // 5. 把锁的值改成订单号，后续 60 秒内的重复提交返回同一笔
            stringRedisTemplate.opsForValue().set(key, String.valueOf(order.getId()),
                    Duration.ofSeconds(SUBMIT_LOCK_SECONDS));

            result.setOrderId(order.getId());
            result.setPayOrderId(order.getPayOrderId());
            result.setCustomizationOrderId(row.getId());
            return result;
        } catch (RuntimeException ex) {
            // 建单失败释放锁，允许重试
            stringRedisTemplate.delete(key);
            throw ex;
        }
    }

    @Override
    public CustomizationOrderDO getByOrderId(Long orderId) {
        return customizationOrderMapper.selectByOrderId(orderId);
    }

    @Override
    public List<CustomizationOrderDO> getListByOrderIds(Collection<Long> orderIds) {
        return customizationOrderMapper.selectListByOrderIds(orderIds);
    }

    private AppTradeOrderCreateReqVO buildTradeOrderReq(AppCustomizationOrderCreateReqVO reqVO) {
        AppTradeOrderCreateReqVO createReqVO = new AppTradeOrderCreateReqVO();
        AppTradeOrderCreateReqVO.Item item = new AppTradeOrderCreateReqVO.Item();
        item.setSkuId(HIDDEN_SKU_ID);
        item.setCount(reqVO.getQuantity());
        createReqVO.setItems(List.of(item));
        // 定制单不参与优惠券与积分（R96）
        createReqVO.setPointStatus(false);
        createReqVO.setDeliveryType(reqVO.getDeliveryType() != null
                ? reqVO.getDeliveryType() : DeliveryTypeEnum.EXPRESS.getType());
        createReqVO.setAddressId(reqVO.getAddressId());
        createReqVO.setInvoice(reqVO.getInvoice());
        createReqVO.setCustomization(reqVO);
        return createReqVO;
    }

    private CustomizationOrderCreateResultBO readDuplicated(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null || LOCK_PENDING.equals(value)) {
            return null;
        }
        Long orderId;
        try {
            orderId = Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
        TradeOrderDO order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            return null;
        }
        CustomizationOrderCreateResultBO result = new CustomizationOrderCreateResultBO();
        result.setDuplicated(true);
        result.setOrderId(order.getId());
        result.setPayOrderId(order.getPayOrderId());
        CustomizationOrderDO row = customizationOrderMapper.selectByOrderId(orderId);
        result.setCustomizationOrderId(row == null ? null : row.getId());
        return result;
    }

    /** 配置指纹：材质 + 宽高 + 数量 + 工艺 + 文件 URL + 备注 */
    static String specHash(CustomizationSpecBO spec) {
        StringBuilder sb = new StringBuilder();
        sb.append(spec.getMaterialId()).append('|')
                .append(spec.getWidth()).append('|').append(spec.getHeight()).append('|')
                .append(spec.getQuantity()).append('|')
                .append(spec.getMode()).append('|')
                .append(spec.getForm()).append('|')
                .append(spec.getRowCount()).append('|').append(spec.getColumnCount()).append('|')
                .append(spec.getFinishing()).append('|')
                .append(Boolean.TRUE.equals(spec.getTearLine())).append('|')
                .append(Boolean.TRUE.equals(spec.getDieCut())).append('|')
                .append(Boolean.TRUE.equals(spec.getUrgent())).append('|')
                .append(spec.getRemark() == null ? "" : spec.getRemark()).append('|');
        if (spec.getFiles() != null) {
            spec.getFiles().forEach(file -> sb.append(file.getUrl()).append(';'));
        }
        return DigestUtil.sha256Hex(sb.toString());
    }

}
