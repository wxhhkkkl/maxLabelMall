package cn.iocoder.yudao.module.trade.service.order;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.ip.core.Area;
import cn.iocoder.yudao.framework.ip.core.utils.AreaUtils;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderPageReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderSummaryRespVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderPageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderRefundStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderTypeEnum;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.ExpressClientFactory;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressMapQueryReqDTO;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressTrackQueryReqDTO;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressTrackRespDTO;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.EXPRESS_MAP_NOT_AVAILABLE;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.EXPRESS_MAP_NOT_CONFIGURED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.EXPRESS_NOT_EXISTS;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.EXPRESS_QUERY_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.ORDER_NOT_FOUND;

/**
 * 交易订单【读】 Service 实现类
 *
 * @author 芋道源码
 */
@Slf4j
@Service
public class TradeOrderQueryServiceImpl implements TradeOrderQueryService {

    @Resource
    private ExpressClientFactory expressClientFactory;

    @Resource
    private TradeOrderMapper tradeOrderMapper;
    @Resource
    private TradeOrderItemMapper tradeOrderItemMapper;

    @Resource
    private DeliveryExpressService deliveryExpressService;

    @Resource
    private MemberUserApi memberUserApi;

    @Resource
    private cn.iocoder.yudao.module.trade.framework.delivery.config.TradeExpressProperties tradeExpressProperties;

    // =================== Order ===================

    @Override
    public TradeOrderDO getOrder(Long id) {
        return tradeOrderMapper.selectById(id);
    }

    @Override
    public TradeOrderDO getOrder(Long userId, Long id) {
        TradeOrderDO order = tradeOrderMapper.selectById(id);
        if (order != null
                && ObjectUtil.notEqual(order.getUserId(), userId)) {
            return null;
        }
        return order;
    }

    @Override
    public TradeOrderDO getOrderByUserIdAndStatusAndCombination(Long userId, Long combinationActivityId, Integer status) {
        return tradeOrderMapper.selectByUserIdAndCombinationActivityIdAndStatus(userId, combinationActivityId, status);
    }

    @Override
    public List<TradeOrderDO> getOrderList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return tradeOrderMapper.selectByIds(ids);
    }

    @Override
    public PageResult<TradeOrderDO> getOrderPage(TradeOrderPageReqVO reqVO) {
        // 根据用户查询条件构建用户编号列表
        Set<Long> userIds = buildQueryConditionUserIds(reqVO);
        if (userIds == null) { // 没查询到用户，说明肯定也没他的订单
            return PageResult.empty();
        }

        // 分页查询
        return tradeOrderMapper.selectPage(reqVO, userIds);
    }

    private Set<Long> buildQueryConditionUserIds(TradeOrderPageReqVO reqVO) {
        // 获得 userId 相关的查询
        Set<Long> userIds = new HashSet<>();
        if (StrUtil.isNotEmpty(reqVO.getUserMobile())) {
            MemberUserRespDTO user = memberUserApi.getUserByMobile(reqVO.getUserMobile()).getCheckedData();
            if (user == null) { // 没查询到用户，说明肯定也没他的订单
                return null;
            }
            userIds.add(user.getId());
        }
        if (StrUtil.isNotEmpty(reqVO.getUserNickname())) {
            List<MemberUserRespDTO> users = memberUserApi.getUserListByNickname(reqVO.getUserNickname()).getCheckedData();
            if (CollUtil.isEmpty(users)) { // 没查询到用户，说明肯定也没他的订单
                return null;
            }
            userIds.addAll(convertSet(users, MemberUserRespDTO::getId));
        }
        return userIds;
    }

    @Override
    public TradeOrderSummaryRespVO getOrderSummary(TradeOrderPageReqVO reqVO) {
        // 根据用户查询条件构建用户编号列表
        Set<Long> userIds = buildQueryConditionUserIds(reqVO);
        if (userIds == null) { // 没查询到用户，说明肯定也没他的订单
            return new TradeOrderSummaryRespVO();
        }
        // 查询每个售后状态对应的数量、金额
        List<Map<String, Object>> list = tradeOrderMapper.selectOrderSummaryGroupByRefundStatus(reqVO, userIds);

        TradeOrderSummaryRespVO vo = new TradeOrderSummaryRespVO().setAfterSaleCount(0L).setAfterSalePrice(0L);
        for (Map<String, Object> map : list) {
            Long count = MapUtil.getLong(map, "count", 0L);
            Long price = MapUtil.getLong(map, "price", 0L);
            // 未退款的计入订单，部分退款、全部退款计入售后
            if (TradeOrderRefundStatusEnum.NONE.getStatus().equals(MapUtil.getInt(map, "refundStatus"))) {
                vo.setOrderCount(count).setOrderPayPrice(price);
            } else {
                vo.setAfterSaleCount(vo.getAfterSaleCount() + count).setAfterSalePrice(vo.getAfterSalePrice() + price);
            }
        }
        return vo;
    }

    @Override
    public PageResult<TradeOrderDO> getOrderPage(Long userId, AppTradeOrderPageReqVO reqVO) {
        return tradeOrderMapper.selectPage(reqVO, userId);
    }

    @Override
    public Long getOrderCount(Long userId, Integer status, Boolean commentStatus) {
        return tradeOrderMapper.selectCountByUserIdAndStatus(userId, status, commentStatus);
    }

    @Override
    public List<ExpressTrackRespDTO> getExpressTrackList(Long id, Long userId) {
        // 查询订单
        TradeOrderDO order = tradeOrderMapper.selectByIdAndUserId(id, userId);
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        // 查询物流
        return getExpressTrackList(order);
    }

    @Override
    public List<ExpressTrackRespDTO> getExpressTrackList(Long id) {
        // 查询订单
        TradeOrderDO order = tradeOrderMapper.selectById(id);
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        // 查询物流
        return getExpressTrackList(order);
    }

    @Override
    public int getActivityProductCount(Long userId, Long activityId, TradeOrderTypeEnum type) {
        // 获得订单列表
        List<TradeOrderDO> orders = tradeOrderMapper.selectListByUserIdAndActivityId(userId, activityId, type);
        orders.removeIf(order -> TradeOrderStatusEnum.isCanceled(order.getStatus())); // 过滤掉【已取消】的订单
        if (CollUtil.isEmpty(orders)) {
            return 0;
        }
        // 获得订单项列表
        return tradeOrderItemMapper.selectProductSumByOrderId(convertSet(orders, TradeOrderDO::getId));
    }

    /**
     * 获得订单的物流轨迹
     *
     * @param order 订单
     * @return 物流轨迹
     */
    private List<ExpressTrackRespDTO> getExpressTrackList(TradeOrderDO order) {
        if (order.getLogisticsId() == null) {
            return Collections.emptyList();
        }
        // 查询物流公司
        DeliveryExpressDO express = deliveryExpressService.getDeliveryExpress(order.getLogisticsId());
        if (express == null) {
            throw exception(EXPRESS_NOT_EXISTS);
        }
        // ⚠️ 用 kd100_code，不是 code：code 是微信小程序物流同步用的短码（E11 / R48）
        String kd100Code = express.getKd100Code();
        if (StrUtil.isEmpty(kd100Code)) {
            // 该快递公司没有配置快递100 编码（如分不清产品线的「安能」）—— 不查、也不编造
            throw exception(EXPRESS_QUERY_NOT_SUPPORTED);
        }
        // 查询物流轨迹；失败时记下订单号便于排查，但**不把三方原文带进异常**（FR-259 / FR-274）
        try {
            return getSelf().getExpressTrackList(kd100Code, order.getLogisticsNo(), order.getReceiverMobile());
        } catch (ServiceException ex) {
            log.warn("[getExpressTrackList][订单({}) 快递公司({}/{}) 查询失败({})]",
                    order.getNo(), express.getName(), kd100Code, ex.getMessage());
            throw ex;
        }
    }

    /**
     * 查询物流轨迹
     * <p>
     * 缓存的目的：考虑及时性要求不高，但是每次调用需要钱
     *
     * @param code           快递公司编码
     * @param logisticsNo    发货快递单号
     * @param receiverMobile 收、寄件人的电话号码
     * @return 物流轨迹
     */
    @Cacheable(cacheNames = RedisKeyConstants.EXPRESS_TRACK, key = "#code + '-' + #logisticsNo + '-' + #receiverMobile",
            unless = "#result == null")
    public List<ExpressTrackRespDTO> getExpressTrackList(String code, String logisticsNo, String receiverMobile) {
        List<ExpressTrackRespDTO> tracks = new ArrayList<>(expressClientFactory.getDefaultExpressClient()
                .getExpressTrackList(new ExpressTrackQueryReqDTO()
                        .setExpressCode(code).setLogisticsNo(logisticsNo).setPhone(receiverMobile)));
        // 按发生时间倒序（规格 FR-253：最新在最上面）；时间缺失的放最后。
        // 先复制再排序：客户端的返回值可能是不可变列表（缓存/测试桩都会），别原地改它。
        tracks.sort(Comparator.comparing(ExpressTrackRespDTO::getTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return tracks;
    }

    // =================== 物流地图 ===================

    @Override
    public String getExpressMapUrl(Long id, Long userId) {
        TradeOrderDO order = tradeOrderMapper.selectByIdAndUserId(id, userId);
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        return getExpressMapUrl(order);
    }

    @Override
    public String getExpressMapUrl(Long id) {
        TradeOrderDO order = tradeOrderMapper.selectById(id);
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        return getExpressMapUrl(order);
    }

    private String getExpressMapUrl(TradeOrderDO order) {
        if (order.getLogisticsId() == null) {
            throw exception(EXPRESS_MAP_NOT_AVAILABLE);
        }
        DeliveryExpressDO express = deliveryExpressService.getDeliveryExpress(order.getLogisticsId());
        if (express == null) {
            throw exception(EXPRESS_NOT_EXISTS);
        }
        String kd100Code = express.getKd100Code();
        if (StrUtil.isEmpty(kd100Code)) {
            throw exception(EXPRESS_QUERY_NOT_SUPPORTED);
        }
        // 发货地址：优先用发货时后台填写的；历史/未填时用配置里的默认发货仓
        Integer fromAreaId = order.getDeliveryFromAreaId();
        if (fromAreaId == null && tradeExpressProperties.getKd100() != null) {
            fromAreaId = tradeExpressProperties.getKd100().getDefaultFromAreaId();
        }
        if (fromAreaId == null) {
            throw exception(EXPRESS_MAP_NOT_CONFIGURED);
        }
        String from = formatAreaForMap(fromAreaId);
        if (StrUtil.isEmpty(from)) {
            throw exception(EXPRESS_MAP_NOT_AVAILABLE);
        }
        // 收件地址；历史订单（receiver_area_id 为空）不显示地图，避免猜地址
        String to = formatAreaForMap(order.getReceiverAreaId());
        if (StrUtil.isEmpty(to)) {
            throw exception(EXPRESS_MAP_NOT_AVAILABLE);
        }
        try {
            String url = getSelf().getExpressMapUrl(kd100Code, order.getLogisticsNo(), order.getReceiverMobile(), from, to);
            if (StrUtil.isEmpty(url)) {
                throw exception(EXPRESS_MAP_NOT_AVAILABLE);
            }
            return url;
        } catch (ServiceException ex) {
            log.warn("[getExpressMapUrl][订单({}) 快递公司({}/{}) 查询失败({})]",
                    order.getNo(), express.getName(), kd100Code, ex.getMessage());
            throw ex;
        }
    }

    /**
     * 把地区编号格式化成快递100 地图要的「省市区」，**去掉直辖市的重复市名**。
     *
     * 例：110108（北京市/北京市/海淀区）→「北京市海淀区」，140400 →「山西省长治市」。
     * 直接用 {@link AreaUtils#format} 会得到「北京市北京市海淀区」，快递100 不认。
     */
    private String formatAreaForMap(Integer areaId) {
        Area area = AreaUtils.getArea(areaId);
        if (area == null) {
            return "";
        }
        LinkedList<String> names = new LinkedList<>();
        while (area != null) {
            if (Area.ID_GLOBAL.equals(area.getId()) || Area.ID_CHINA.equals(area.getId())) {
                break;
            }
            if (names.isEmpty() || !names.getFirst().equals(area.getName())) {
                names.addFirst(area.getName());
            }
            area = area.getParent();
        }
        return String.join("", names);
    }

    /**
     * 查询物流地图地址
     *
     * 缓存目的与轨迹一致：地图查询是独立计费的接口。
     */
    @Cacheable(cacheNames = RedisKeyConstants.EXPRESS_MAP,
            key = "#code + '-' + #logisticsNo + '-' + #phone + '-' + #from + '-' + #to",
            unless = "#result == null")
    public String getExpressMapUrl(String code, String logisticsNo, String phone, String from, String to) {
        return expressClientFactory.getDefaultExpressClient().getExpressMapUrl(new ExpressMapQueryReqDTO()
                .setExpressCode(code).setLogisticsNo(logisticsNo).setPhone(phone).setFrom(from).setTo(to));
    }

    // =================== Order Item ===================

    @Override
    public TradeOrderItemDO getOrderItem(Long userId, Long itemId) {
        TradeOrderItemDO orderItem = tradeOrderItemMapper.selectById(itemId);
        if (orderItem != null
                && ObjectUtil.notEqual(orderItem.getUserId(), userId)) {
            return null;
        }
        return orderItem;
    }

    @Override
    public TradeOrderItemDO getOrderItem(Long id) {
        return tradeOrderItemMapper.selectById(id);
    }

    @Override
    public List<TradeOrderItemDO> getOrderItemListByOrderId(Collection<Long> orderIds) {
        if (CollUtil.isEmpty(orderIds)) {
            return Collections.emptyList();
        }
        return tradeOrderItemMapper.selectListByOrderId(orderIds);
    }

    /**
     * 获得自身的代理对象，解决 AOP 生效问题
     *
     * @return 自己
     */
    private TradeOrderQueryServiceImpl getSelf() {
        return SpringUtil.getBean(getClass());
    }

}
