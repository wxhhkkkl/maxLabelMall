package cn.iocoder.yudao.module.trade.dal.mysql.order;

import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderPageReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderPageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderTypeEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mapper
public interface TradeOrderMapper extends BaseMapperX<TradeOrderDO> {

    default int updateByIdAndStatus(Long id, Integer status, TradeOrderDO update) {
        return update(update, new LambdaUpdateWrapper<TradeOrderDO>()
                .eq(TradeOrderDO::getId, id).eq(TradeOrderDO::getStatus, status));
    }

    default TradeOrderDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(TradeOrderDO::getId, id, TradeOrderDO::getUserId, userId);
    }

    /**
     * 「支付渠道」筛选的子查询（008 / FR-209）
     *
     * ⚠️ **筛选「对公转账」MUST NOT 按 `trade_order.pay_channel_code` 来** ——
     * 那一列的语义是"**支付成功**的支付渠道"（建表注释如此），对公单在**确认收款前它是空的**，
     * 照它筛会把最该被财务看到的「待收款」单**全部漏掉**。
     *
     * 前端传的仍然是 `payChannelCode=offline`（运营看得懂的说法），**转换在服务端做**。
     */
    String OFFLINE_ORDER_IDS_SQL = "SELECT order_id FROM trade_order_offline_payment WHERE deleted = 0";

    /**
     * 应用「支付渠道」筛选 —— 对公转账走**子表子查询**，其余渠道照旧按列筛（008）
     *
     * ⚠️ **页面与统计两处共用这一个方法**：只改一处，就会出现"列表筛得出、统计数不对"
     * （与自动取消那两处是同一类坑，见 research R45）。
     */
    @SuppressWarnings("unchecked")
    default LambdaQueryWrapperX<TradeOrderDO> applyPayChannelCodeFilter(LambdaQueryWrapperX<TradeOrderDO> query,
                                                                       String payChannelCode) {
        if (PayChannelEnum.OFFLINE.getCode().equals(payChannelCode)) {
            return (LambdaQueryWrapperX<TradeOrderDO>) query.inSql(TradeOrderDO::getId, OFFLINE_ORDER_IDS_SQL);
        }
        return query.eqIfPresent(TradeOrderDO::getPayChannelCode, payChannelCode);
    }

    /** {@link #applyPayChannelCodeFilter(LambdaQueryWrapperX, String)} 的联表版本（统计用） */
    default MPJLambdaWrapper<TradeOrderDO> applyPayChannelCodeFilter(MPJLambdaWrapper<TradeOrderDO> query,
                                                                     String payChannelCode) {
        if (PayChannelEnum.OFFLINE.getCode().equals(payChannelCode)) {
            query.inSql(TradeOrderDO::getId, OFFLINE_ORDER_IDS_SQL);
            return query;
        }
        // 与 `MPJLambdaWrapperX#eqIfPresent` **同一个条件**（空串/ null 都不拼）
        if (ObjectUtil.isNotEmpty(payChannelCode)) {
            query.eq(TradeOrderDO::getPayChannelCode, payChannelCode);
        }
        return query;
    }

    default PageResult<TradeOrderDO> selectPage(TradeOrderPageReqVO reqVO, Set<Long> userIds) {
        return selectPage(reqVO, applyPayChannelCodeFilter(new LambdaQueryWrapperX<TradeOrderDO>()
                .likeIfPresent(TradeOrderDO::getNo, reqVO.getNo())
                .eqIfPresent(TradeOrderDO::getUserId, reqVO.getUserId())
                .eqIfPresent(TradeOrderDO::getDeliveryType, reqVO.getDeliveryType())
                .inIfPresent(TradeOrderDO::getUserId, userIds)
                .eqIfPresent(TradeOrderDO::getType, reqVO.getType())
                .eqIfPresent(TradeOrderDO::getStatus, reqVO.getStatus())
                // 状态多选（顶部标签页「待发货/已发货」= [10,20]）：与上面的单选是或的关系
                .inIfPresent(TradeOrderDO::getStatus, reqVO.getStatuses())
                // ⚠️ 支付渠道**挪到 `applyPayChannelCodeFilter`** 里了（008）：见那里的说明
                .eqIfPresent(TradeOrderDO::getTerminal, reqVO.getTerminal())
                .eqIfPresent(TradeOrderDO::getLogisticsId, reqVO.getLogisticsId())
                .inIfPresent(TradeOrderDO::getPickUpStoreId, reqVO.getPickUpStoreIds())
                .likeIfPresent(TradeOrderDO::getPickUpVerifyCode, reqVO.getPickUpVerifyCode())
                .betweenIfPresent(TradeOrderDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(TradeOrderDO::getId), reqVO.getPayChannelCode()));
    }

    // TODO @疯狂：如果用 map 返回，要不这里直接用 TradeOrderSummaryRespVO 返回？也算合理，就当  sql 查询出这么个玩意~~
    default List<Map<String, Object>> selectOrderSummaryGroupByRefundStatus(TradeOrderPageReqVO reqVO, Set<Long> userIds) {
        return selectMaps(applyPayChannelCodeFilter(new MPJLambdaWrapperX<TradeOrderDO>()
                .selectAs(TradeOrderDO::getRefundStatus, TradeOrderDO::getRefundStatus)  // 售后状态
                .selectCount(TradeOrderDO::getId, "count") // 售后状态对应的数量
                .selectSum(TradeOrderDO::getPayPrice, "price")  // 售后状态对应的支付金额
                .likeIfPresent(TradeOrderDO::getNo, reqVO.getNo())
                .eqIfPresent(TradeOrderDO::getUserId, reqVO.getUserId())
                .eqIfPresent(TradeOrderDO::getDeliveryType, reqVO.getDeliveryType())
                .inIfPresent(TradeOrderDO::getUserId, userIds)
                .eqIfPresent(TradeOrderDO::getType, reqVO.getType())
                .eqIfPresent(TradeOrderDO::getStatus, reqVO.getStatus())
                // 状态多选（顶部标签页「待发货/已发货」= [10,20]）：与上面的单选是或的关系
                .inIfPresent(TradeOrderDO::getStatus, reqVO.getStatuses())
                // ⚠️ 支付渠道**挪到 `applyPayChannelCodeFilter`** 里了（008）——
                //    与列表**共用同一处实现**，否则会出现"列表筛得出、统计数不对"
                .eqIfPresent(TradeOrderDO::getTerminal, reqVO.getTerminal())
                .eqIfPresent(TradeOrderDO::getLogisticsId, reqVO.getLogisticsId())
                .inIfPresent(TradeOrderDO::getPickUpStoreId, reqVO.getPickUpStoreIds())
                .likeIfPresent(TradeOrderDO::getPickUpVerifyCode, reqVO.getPickUpVerifyCode())
                .betweenIfPresent(TradeOrderDO::getCreateTime, reqVO.getCreateTime())
                .groupBy(TradeOrderDO::getRefundStatus), reqVO.getPayChannelCode())); // 按售后状态分组
    }

    default PageResult<TradeOrderDO> selectPage(AppTradeOrderPageReqVO reqVO, Long userId) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TradeOrderDO>()
                .eq(TradeOrderDO::getUserId, userId)
                .eqIfPresent(TradeOrderDO::getStatus, reqVO.getStatus())
                .eqIfPresent(TradeOrderDO::getCommentStatus, reqVO.getCommentStatus())
                .orderByDesc(TradeOrderDO::getId)); // TODO 芋艿：未来不同的 status，不同的排序
    }

    default Long selectCountByUserIdAndStatus(Long userId, Integer status, Boolean commentStatus) {
        return selectCount(new LambdaQueryWrapperX<TradeOrderDO>()
                .eq(TradeOrderDO::getUserId, userId)
                .eqIfPresent(TradeOrderDO::getStatus, status)
                .eqIfPresent(TradeOrderDO::getCommentStatus, commentStatus));
    }

    default TradeOrderDO selectOrderByIdAndUserId(Long orderId, Long loginUserId) {
        return selectOne(new LambdaQueryWrapperX<TradeOrderDO>()
                .eq(TradeOrderDO::getId, orderId)
                .eq(TradeOrderDO::getUserId, loginUserId));
    }

    default List<TradeOrderDO> selectListByStatusAndCreateTimeLt(Integer status, LocalDateTime createTime) {
        return selectList(new LambdaUpdateWrapper<TradeOrderDO>()
                .eq(TradeOrderDO::getStatus, status)
                .lt(TradeOrderDO::getCreateTime, createTime));
    }

    default List<TradeOrderDO> selectListByStatusAndDeliveryTimeLt(Integer status, LocalDateTime deliveryTime) {
        return selectList(new LambdaUpdateWrapper<TradeOrderDO>()
                .eq(TradeOrderDO::getStatus, status)
                .lt(TradeOrderDO::getDeliveryTime, deliveryTime));
    }

    default List<TradeOrderDO> selectListByStatusAndReceiveTimeLt(Integer status, LocalDateTime receive,
                                                                  Boolean commentStatus) {
        return selectList(new LambdaUpdateWrapper<TradeOrderDO>()
                .eq(TradeOrderDO::getStatus, status)
                .lt(TradeOrderDO::getReceiveTime, receive)
                .eq(TradeOrderDO::getCommentStatus, commentStatus));
    }

    default List<TradeOrderDO> selectListByUserIdAndActivityId(Long userId, Long activityId, TradeOrderTypeEnum type) {
        LambdaQueryWrapperX<TradeOrderDO> queryWrapperX = new LambdaQueryWrapperX<>();
        queryWrapperX.eq(TradeOrderDO::getUserId, userId);
        if (TradeOrderTypeEnum.isSeckill(type.getType())) {
            queryWrapperX.eq(TradeOrderDO::getSeckillActivityId, activityId);
        }
        if (TradeOrderTypeEnum.isBargain(type.getType())) {
            queryWrapperX.eq(TradeOrderDO::getBargainActivityId, activityId);
        }
        if (TradeOrderTypeEnum.isCombination(type.getType())) {
            queryWrapperX.eq(TradeOrderDO::getCombinationActivityId, activityId);
        }
        if (TradeOrderTypeEnum.isPoint(type.getType())) {
            queryWrapperX.eq(TradeOrderDO::getPointActivityId, activityId);
        }
        return selectList(queryWrapperX);
    }

    default TradeOrderDO selectOneByPickUpVerifyCode(String pickUpVerifyCode) {
        return selectOne(TradeOrderDO::getPickUpVerifyCode, pickUpVerifyCode);
    }

    default TradeOrderDO selectByUserIdAndCombinationActivityIdAndStatus(Long userId, Long combinationActivityId, Integer status) {
        return selectOne(new LambdaQueryWrapperX<TradeOrderDO>()
                .eq(TradeOrderDO::getUserId, userId)
                .eq(TradeOrderDO::getStatus, status)
                .eq(TradeOrderDO::getCombinationActivityId, combinationActivityId)
        );
    }

}
