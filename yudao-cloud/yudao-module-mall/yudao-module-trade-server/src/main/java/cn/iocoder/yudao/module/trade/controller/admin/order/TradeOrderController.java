package cn.iocoder.yudao.module.trade.controller.admin.order;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.ip.core.utils.AreaUtils;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.*;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderSettlementRespVO;
import cn.iocoder.yudao.module.trade.framework.delivery.config.TradeExpressProperties;
import cn.iocoder.yudao.module.trade.convert.invoice.InvoiceConvert;
import cn.iocoder.yudao.module.trade.convert.order.TradeOrderConvert;
import cn.iocoder.yudao.module.trade.dal.dataobject.invoice.TradeOrderInvoiceDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.offline.TradeOrderOfflinePaymentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderLogDO;
import cn.iocoder.yudao.module.trade.enums.invoice.TradeOrderInvoiceStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.service.invoice.TradeOrderInvoiceService;
import cn.iocoder.yudao.module.trade.service.offline.TradeOrderOfflinePaymentService;
import cn.iocoder.yudao.module.trade.service.order.AgentOrderService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderLogService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.pojo.PageParam.PAGE_SIZE_NONE;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 交易订单")
@RestController
@RequestMapping("/trade/order")
@Validated
@Slf4j
public class TradeOrderController {

    @Resource
    private TradeOrderUpdateService tradeOrderUpdateService;
    @Resource
    private TradeOrderQueryService tradeOrderQueryService;
    @Resource
    private cn.iocoder.yudao.module.trade.service.customization.CustomizationOrderService customizationOrderService;
    @Resource
    private cn.iocoder.yudao.module.trade.framework.delivery.config.TradeExpressProperties tradeExpressProperties;
    @Resource
    private TradeOrderLogService tradeOrderLogService;

    @Resource
    private MemberUserApi memberUserApi;
    @Resource
    private TradeOrderOfflinePaymentService tradeOrderOfflinePaymentService;
    @Resource
    private TradeOrderInvoiceService tradeOrderInvoiceService;
    @Resource
    private AgentOrderService agentOrderService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private PayOrderApi payOrderApi;

    @GetMapping("/page")
    @Operation(summary = "获得交易订单分页")
    @PreAuthorize("@ss.hasPermission('trade:order:query')")
    public CommonResult<PageResult<TradeOrderPageItemRespVO>> getOrderPage(TradeOrderPageReqVO reqVO) {
        // 查询订单
        PageResult<TradeOrderDO> pageResult = tradeOrderQueryService.getOrderPage(reqVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty());
        }

        // 查询用户信息
        Set<Long> userIds = CollUtil.unionDistinct(convertList(pageResult.getList(), TradeOrderDO::getUserId),
                convertList(pageResult.getList(), TradeOrderDO::getBrokerageUserId, Objects::nonNull));
        Map<Long, MemberUserRespDTO> userMap = memberUserApi.getUserMap(userIds);
        // 查询订单项
        List<TradeOrderItemDO> orderItems = tradeOrderQueryService.getOrderItemListByOrderId(
                convertSet(pageResult.getList(), TradeOrderDO::getId));
        // 最终组合
        PageResult<TradeOrderPageItemRespVO> page = TradeOrderConvert.INSTANCE.convertPage(pageResult, orderItems, userMap);

        // 开票状态（006 / FR-178）：**一次 IN 批量查**，与 C 端订单列表同一手法。
        // 未申请的订单拿不到值 → 字段保持 null → 列表显示「否」
        Map<Long, Integer> invoiceStatusMap = tradeOrderInvoiceService.getInvoiceStatusMapByOrderIds(
                convertSet(page.getList(), TradeOrderPageItemRespVO::getId));
        page.getList().forEach(item -> item.setInvoiceStatus(invoiceStatusMap.get(item.getId())));

        // 对公转账「待收款」（008 / FR-209）：**一次 IN 批量查**，与 C 端同一手法
        Set<Long> waitingOrderIds = tradeOrderOfflinePaymentService.getWaitingReceiptOrderIds(
                convertSet(page.getList(), TradeOrderPageItemRespVO::getId));
        page.getList().forEach(item -> item.setOfflineWaiting(
                waitingOrderIds.contains(item.getId())
                        && TradeOrderStatusEnum.isUnpaid(item.getStatus())));
        // 代下单人昵称（017）：一次批量解析；账号不存在时为 null，前端显示 后台用户#{id}
        fillAgentUserNames(page.getList());
        return success(page);
    }

    /**
     * 导出交易订单 Excel（2026-11-10 所有者口径：**仅按下单时间范围**筛选）
     *
     * <p>列表上的状态、支付方式等筛选条件一律不参与 —— 运营选完时间就得到该时间段的全部订单。
     * 权限沿用 `trade:order:query`（能看订单就能导出，不新增权限点）。
     */
    @GetMapping("/export-excel")
    @Operation(summary = "导出交易订单 Excel")
    @PreAuthorize("@ss.hasPermission('trade:order:query')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportOrderExcel(@Valid TradeOrderExportReqVO exportReqVO,
                                 HttpServletResponse response) throws IOException {
        // ⚠️ 只把时间范围带过去，不把列表筛选条件塞进来（口径见方法注释）
        TradeOrderPageReqVO pageReqVO = new TradeOrderPageReqVO();
        pageReqVO.setCreateTime(exportReqVO.getCreateTime());
        pageReqVO.setPageSize(PAGE_SIZE_NONE);
        List<TradeOrderDO> orders = tradeOrderQueryService.getOrderPage(pageReqVO).getList();

        List<TradeOrderExcelVO> dataList = buildOrderExcelList(orders);
        ExcelUtils.write(response, "交易订单.xls", "数据", TradeOrderExcelVO.class, dataList);
    }

    /**
     * 组装导出的行：**一笔订单一行**，商品名称合并、数量汇总
     */
    List<TradeOrderExcelVO> buildOrderExcelList(List<TradeOrderDO> orders) {
        if (CollUtil.isEmpty(orders)) {
            return Collections.emptyList();
        }
        // 订单项：一次 IN 查询，避免逐笔查（与列表页同一手法）
        Map<Long, List<TradeOrderItemDO>> orderItemMap = tradeOrderQueryService
                .getOrderItemListByOrderId(convertSet(orders, TradeOrderDO::getId))
                .stream().collect(Collectors.groupingBy(TradeOrderItemDO::getOrderId));
        // 会员昵称
        Map<Long, MemberUserRespDTO> userMap = memberUserApi.getUserMap(
                convertSet(orders, TradeOrderDO::getUserId, Objects::nonNull));
        // 代下单人
        Set<Long> agentUserIds = convertSet(orders, TradeOrderDO::getAgentUserId, Objects::nonNull);
        Map<Long, AdminUserRespDTO> agentUserMap = CollUtil.isEmpty(agentUserIds)
                ? Collections.emptyMap() : adminUserApi.getUserMap(agentUserIds);
        // 开票状态：一次 IN 批量查
        Map<Long, Integer> invoiceStatusMap = tradeOrderInvoiceService.getInvoiceStatusMapByOrderIds(
                convertSet(orders, TradeOrderDO::getId));

        return convertList(orders, order -> {
            TradeOrderExcelVO vo = new TradeOrderExcelVO();
            vo.setNo(order.getNo());
            vo.setCreateTime(order.getCreateTime());
            vo.setStatus(order.getStatus());
            vo.setPayChannelCode(order.getPayChannelCode());
            vo.setPayTime(order.getPayTime());
            vo.setUserId(order.getUserId());
            MemberUserRespDTO user = userMap.get(order.getUserId());
            vo.setUserNickname(user != null ? user.getNickname() : null);

            // 下单方式 + 代下单人（017）
            if (order.getAgentUserId() != null) {
                vo.setOrderSource("后台代客");
                AdminUserRespDTO agentUser = agentUserMap.get(order.getAgentUserId());
                vo.setAgentUserName(agentUser != null && StrUtil.isNotBlank(agentUser.getNickname())
                        ? agentUser.getNickname() : "后台用户#" + order.getAgentUserId());
            } else {
                vo.setOrderSource("客户自助");
            }

            // 商品：名称合并、数量汇总
            List<TradeOrderItemDO> items = orderItemMap.getOrDefault(order.getId(), Collections.emptyList());
            vo.setProductNames(items.stream()
                    .map(TradeOrderItemDO::getSpuName)
                    .filter(StrUtil::isNotBlank)
                    .distinct()
                    .collect(Collectors.joining("、")));
            vo.setProductCount(ObjectUtils.defaultIfNull(order.getProductCount(), 0));

            // 金额：统一转成非 null（MoneyConvert 遇到 null 会 NPE）
            vo.setTotalPrice(ObjectUtils.defaultIfNull(order.getTotalPrice(), 0));
            vo.setDeliveryPrice(ObjectUtils.defaultIfNull(order.getDeliveryPrice(), 0));
            vo.setDiscountPrice(ObjectUtils.defaultIfNull(order.getDiscountPrice(), 0));
            vo.setPayPrice(ObjectUtils.defaultIfNull(order.getPayPrice(), 0));
            vo.setRefundPrice(ObjectUtils.defaultIfNull(order.getRefundPrice(), 0));

            // 收件信息 + 物流
            vo.setReceiverName(order.getReceiverName());
            vo.setReceiverMobile(order.getReceiverMobile());
            vo.setReceiverAddress(formatReceiverAddress(order));
            vo.setDeliveryType(order.getDeliveryType());
            vo.setLogisticsNo(order.getLogisticsNo());
            vo.setDeliveryTime(order.getDeliveryTime());

            // 开票状态（006）：未申请为 null
            Integer invoiceStatus = invoiceStatusMap.get(order.getId());
            TradeOrderInvoiceStatusEnum invoiceStatusEnum = invoiceStatus != null
                    ? TradeOrderInvoiceStatusEnum.valueOf(invoiceStatus) : null;
            vo.setInvoiceStatus(invoiceStatusEnum != null ? invoiceStatusEnum.getName() : "未申请");

            vo.setRemark(order.getRemark());
            return vo;
        });
    }

    /** 收货地址：省市区 + 详细地址；虚拟商品没有地址时不报错 */
    private static String formatReceiverAddress(TradeOrderDO order) {
        String areaName = order.getReceiverAreaId() != null
                ? AreaUtils.format(order.getReceiverAreaId()) : null;
        if (StrUtil.isBlank(areaName)) {
            return order.getReceiverDetailAddress();
        }
        if (StrUtil.isBlank(order.getReceiverDetailAddress())) {
            return areaName;
        }
        return areaName + " " + order.getReceiverDetailAddress();
    }

    private void fillAgentUserNames(List<TradeOrderPageItemRespVO> list) {
        Set<Long> agentUserIds = convertSet(list, TradeOrderPageItemRespVO::getAgentUserId, Objects::nonNull);
        if (CollUtil.isEmpty(agentUserIds)) {
            return;
        }
        Map<Long, AdminUserRespDTO> agentUserMap = adminUserApi.getUserMap(agentUserIds);
        list.forEach(item -> {
            AdminUserRespDTO user = agentUserMap.get(item.getAgentUserId());
            item.setAgentUserName(user != null ? user.getNickname() : null);
        });
    }

    // ==================== 代客下单（017） ====================

    @PostMapping("/agent-settlement")
    @Operation(summary = "代客下单 - 结算预览")
    @PreAuthorize("@ss.hasPermission('trade:order:agent-create')")
    public CommonResult<AppTradeOrderSettlementRespVO> agentSettlement(
            @Valid @RequestBody AdminAgentOrderSettlementReqVO reqVO) {
        return success(agentOrderService.settlement(reqVO));
    }

    @PostMapping("/agent-create")
    @Operation(summary = "代客下单 - 创建订单")
    @PreAuthorize("@ss.hasPermission('trade:order:agent-create')")
    public CommonResult<TradeOrderCreateRespVO> agentCreate(
            @Valid @RequestBody AdminAgentOrderCreateReqVO reqVO) {
        return success(agentOrderService.create(getLoginUserId(), reqVO));
    }

    @PostMapping("/agent-pay")
    @Operation(summary = "代客下单 - 发起在线支付")
    @PreAuthorize("@ss.hasPermission('trade:order:agent-create')")
    public CommonResult<AdminAgentOrderPayRespVO> agentPay(
            @Valid @RequestBody AdminAgentOrderPayReqVO reqVO) {
        return success(agentOrderService.pay(reqVO));
    }

    @GetMapping("/agent-pay-status")
    @Operation(summary = "代客下单 - 查询支付状态")
    @Parameter(name = "orderId", description = "订单编号", required = true)
    @PreAuthorize("@ss.hasPermission('trade:order:agent-create')")
    public CommonResult<AdminAgentOrderPayStatusRespVO> agentPayStatus(@RequestParam("orderId") Long orderId) {
        return success(agentOrderService.getPayStatus(orderId));
    }

    @GetMapping("/summary")
    @Operation(summary = "获得交易订单统计")
    @PreAuthorize("@ss.hasPermission('trade:order:query')")
    public CommonResult<TradeOrderSummaryRespVO> getOrderSummary(TradeOrderPageReqVO reqVO) {
        return success(tradeOrderQueryService.getOrderSummary(reqVO));
    }

    @GetMapping("/get-detail")
    @Operation(summary = "获得交易订单详情")
    @Parameter(name = "id", description = "订单编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('trade:order:query')")
    public CommonResult<TradeOrderDetailRespVO> getOrderDetail(@RequestParam("id") Long id) {
        // 查询订单
        TradeOrderDO order = tradeOrderQueryService.getOrder(id);
        if (order == null) {
            return success(null);
        }
        // 查询订单项
        List<TradeOrderItemDO> orderItems = tradeOrderQueryService.getOrderItemListByOrderId(id);

        // 拼接数据
        MemberUserRespDTO user = memberUserApi.getUser(order.getUserId()).getCheckedData();
        MemberUserRespDTO brokerageUser = order.getBrokerageUserId() != null ?
                memberUserApi.getUser(order.getBrokerageUserId()).getCheckedData() : null;
        List<TradeOrderLogDO> orderLogs = tradeOrderLogService.getOrderLogListByOrderId(id);
        TradeOrderDetailRespVO respVO = TradeOrderConvert.INSTANCE.convert(order, orderItems, orderLogs, user, brokerageUser);
        // 代下单人昵称（017）：账号不存在时为 null，前端显示 后台用户#{id}
        if (order.getAgentUserId() != null) {
            AdminUserRespDTO agentUser = adminUserApi.getUser(order.getAgentUserId()).getData();
            respVO.setAgentUserName(agentUser != null ? agentUser.getNickname() : null);
        }
        // 微信/支付宝交易单号：详情页「付款方式」后展示（内部 payOrderId 之外，运营对账用）
        if (order.getPayOrderId() != null && Boolean.TRUE.equals(order.getPayStatus())) {
            PayOrderRespDTO payOrder = payOrderApi.getOrder(order.getPayOrderId()).getData();
            if (payOrder != null) {
                respVO.setPayChannelOrderNo(payOrder.getChannelOrderNo());
            }
        }
        // 开票详情（006）：一次查询同时给出状态与完整申请内容；未申请时为 null
        TradeOrderInvoiceDO invoice = tradeOrderInvoiceService.getInvoiceByOrderId(id);
        respVO.setInvoiceStatus(invoice != null ? invoice.getStatus() : null);
        respVO.setInvoice(invoice != null ? InvoiceConvert.convertToAdminRespVO(invoice, order) : null);
        // 对公转账信息（008）：待收款标记 + 确认痕迹（FR-205 的可追溯）
        buildOfflinePayment(respVO, order);
        // 印刷定制（014）：不是定制单时为 null
        if (Boolean.TRUE.equals(order.getCustomization())) {
            respVO.setCustomization(true);
            respVO.setCustomizationDetail(buildCustomizationDetail(order.getId()));
        }
        return success(respVO);
    }

    /** 定制要求（014）：含下单时的参数快照 */
    private AdminCustomizationDetailRespVO buildCustomizationDetail(Long orderId) {
        cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationOrderDO row =
                customizationOrderService.getByOrderId(orderId);
        if (row == null) {
            return null;
        }
        AdminCustomizationDetailRespVO vo = new AdminCustomizationDetailRespVO();
        vo.setId(row.getId());
        vo.setMaterialId(row.getMaterialId());
        vo.setMaterialName(row.getMaterialName());
        vo.setWidth(row.getWidth());
        vo.setHeight(row.getHeight());
        vo.setQuantity(row.getQuantity());
        vo.setUnitArea(row.getUnitArea());
        vo.setTotalArea(row.getTotalArea());
        vo.setMode(row.getMode());
        vo.setForm(row.getForm());
        vo.setSheetSpec(row.getSheetSpec());
        vo.setFinishing(row.getFinishing());
        vo.setTearLine(row.getTearLine());
        vo.setRowCount(row.getRowCount());
        vo.setColumnCount(row.getColumnCount());
        vo.setRowGap(row.getRowGap());
        vo.setColumnGap(row.getColumnGap());
        vo.setAxisDiameter(row.getAxisDiameter());
        vo.setLabelCount(row.getLabelCount());
        vo.setTemplateName(row.getTemplateName());
        vo.setDieCut(row.getDieCut());
        vo.setUrgent(row.getUrgent());
        vo.setRemark(row.getRemark());
        vo.setBreakdown(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                row.getPriceBreakdown(), AdminCustomizationDetailRespVO.Breakdown.class));
        vo.setFiles(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseArray(
                row.getFiles(), AdminCustomizationDetailRespVO.File.class));
        vo.setParamSnapshot(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                row.getParamSnapshot(), Object.class));
        return vo;
    }

    /**
     * 拼对公转账信息（008）—— 照 006 挂开票信息的同一手法
     *
     * ⚠️ `offlineWaiting` **由服务端判**（"是对公单 **且** 未支付 **且** 未确认"），
     * 不让前端自己拼三个条件 —— 对公单确认前 `payChannelCode` 是空的，前端根本拼不出来。
     */
    private void buildOfflinePayment(TradeOrderDetailRespVO respVO, TradeOrderDO order) {
        TradeOrderOfflinePaymentDO offlinePayment = tradeOrderOfflinePaymentService.getOfflinePaymentByOrderId(order.getId());
        if (offlinePayment == null) {
            respVO.setOfflineWaiting(false); // 不是对公单 → 两个字段都别让人误会
            return;
        }
        respVO.setOfflineWaiting(offlinePayment.getConfirmTime() == null
                && TradeOrderStatusEnum.isUnpaid(order.getStatus()));
        respVO.setOfflinePayment(BeanUtils.toBean(offlinePayment, TradeOrderOfflinePaymentRespVO.class));
    }

    @GetMapping("/get-express-track-list")
    @Operation(summary = "获得交易订单的物流轨迹")
    @Parameter(name = "id", description = "交易订单编号")
    @PreAuthorize("@ss.hasPermission('trade:order:query')")
    public CommonResult<List<?>> getOrderExpressTrackList(@RequestParam("id") Long id) {
        return success(TradeOrderConvert.INSTANCE.convertList02(
                tradeOrderQueryService.getExpressTrackList(id)));
    }

    @GetMapping("/get-express-map-url")
    @Operation(summary = "获得交易订单的物流地图地址")
    @Parameter(name = "id", description = "交易订单编号")
    @PreAuthorize("@ss.hasPermission('trade:order:query')")
    public CommonResult<String> getExpressMapUrl(@RequestParam("id") Long id) {
        return success(tradeOrderQueryService.getExpressMapUrl(id));
    }

    @GetMapping("/get-delivery-default-address")
    @Operation(summary = "获得发货默认地址")
    @PreAuthorize("@ss.hasPermission('trade:order:update')")
    public CommonResult<TradeOrderDeliveryDefaultRespVO> getDeliveryDefaultAddress() {
        TradeExpressProperties.Kd100Config kd100 = tradeExpressProperties.getKd100();
        return success(new TradeOrderDeliveryDefaultRespVO()
                .setAreaId(kd100 != null ? kd100.getDefaultFromAreaId() : null)
                .setAddress(kd100 != null ? kd100.getDefaultFromAddress() : null));
    }

    @PutMapping("/delivery")
    @Operation(summary = "订单发货")
    @PreAuthorize("@ss.hasPermission('trade:order:update')")
    public CommonResult<Boolean> deliveryOrder(@RequestBody TradeOrderDeliveryReqVO deliveryReqVO) {
        tradeOrderUpdateService.deliveryOrder(deliveryReqVO);
        return success(true);
    }

    @PutMapping("/update-remark")
    @Operation(summary = "订单备注")
    @PreAuthorize("@ss.hasPermission('trade:order:update')")
    public CommonResult<Boolean> updateOrderRemark(@RequestBody TradeOrderRemarkReqVO reqVO) {
        tradeOrderUpdateService.updateOrderRemark(reqVO);
        return success(true);
    }

    @PutMapping("/update-price")
    @Operation(summary = "订单调价")
    @PreAuthorize("@ss.hasPermission('trade:order:update')")
    public CommonResult<Boolean> updateOrderPrice(@RequestBody TradeOrderUpdatePriceReqVO reqVO) {
        tradeOrderUpdateService.updateOrderPrice(reqVO);
        return success(true);
    }

    @PutMapping("/update-address")
    @Operation(summary = "修改订单收货地址")
    @PreAuthorize("@ss.hasPermission('trade:order:update')")
    public CommonResult<Boolean> updateOrderAddress(@RequestBody TradeOrderUpdateAddressReqVO reqVO) {
        tradeOrderUpdateService.updateOrderAddress(reqVO);
        return success(true);
    }

    /**
     * 更新订单 **ERP 开单状态**（2026-11-10）
     *
     * <p>运营手工维护：标记已开单时必填 ERP 单号，撤销时清空。沿用既有的
     * `trade:order:update` 权限点（不新增权限点、无需 SQL 菜单）。
     */
    @PutMapping("/update-erp")
    @Operation(summary = "更新订单ERP开单状态")
    @PreAuthorize("@ss.hasPermission('trade:order:update')")
    public CommonResult<Boolean> updateOrderErp(@Valid @RequestBody TradeOrderErpReqVO reqVO) {
        tradeOrderUpdateService.updateOrderErpStatus(reqVO);
        return success(true);
    }

    /**
     * 确认**对公转账收款**（008 / FR-203）
     *
     * ⚠️ **独立权限点** `trade:order:confirm-paid`：这是**资金动作**，不是所有后台角色都该能做
     * （FR-203）。漏登记权限点 = 连超管都看不到按钮，而接口是通的 —— 只能手工验（quickstart §二.6）。
     */
    @PutMapping("/confirm-offline-paid")
    @Operation(summary = "确认对公转账收款")
    @PreAuthorize("@ss.hasPermission('trade:order:confirm-paid')")
    public CommonResult<Boolean> confirmOfflinePaid(@Valid @RequestBody TradeOrderOfflineConfirmReqVO reqVO) {
        tradeOrderUpdateService.confirmOfflinePaid(reqVO, getLoginUserId());
        return success(true);
    }

    /**
     * **取消订单**（008 / FR-213）
     *
     * 沿用既有的 `trade:order:update` 权限点（不新增 —— 它本来就是个通用的订单操作）。
     */
    @PutMapping("/cancel")
    @Operation(summary = "取消订单")
    @PreAuthorize("@ss.hasPermission('trade:order:update')")
    public CommonResult<Boolean> cancelOrder(@Valid @RequestBody TradeOrderCancelReqVO reqVO) {
        tradeOrderUpdateService.cancelOrderByAdmin(reqVO);
        return success(true);
    }

    @PutMapping("/pick-up-by-id")
    @Operation(summary = "订单核销")
    @Parameter(name = "id", description = "交易订单编号")
    @PreAuthorize("@ss.hasPermission('trade:order:pick-up')")
    public CommonResult<Boolean> pickUpOrderById(@RequestParam("id") Long id) {
        tradeOrderUpdateService.pickUpOrderByAdmin(getLoginUserId(), id);
        return success(true);
    }

    @PutMapping("/pick-up-by-verify-code")
    @Operation(summary = "订单核销")
    @Parameter(name = "pickUpVerifyCode", description = "自提核销码")
    @PreAuthorize("@ss.hasPermission('trade:order:pick-up')")
    public CommonResult<Boolean> pickUpOrderByVerifyCode(@RequestParam("pickUpVerifyCode") String pickUpVerifyCode) {
        tradeOrderUpdateService.pickUpOrderByAdmin(getLoginUserId(), pickUpVerifyCode);
        return success(true);
    }

    @GetMapping("/get-by-pick-up-verify-code")
    @Operation(summary = "查询核销码对应的订单")
    @Parameter(name = "pickUpVerifyCode", description = "自提核销码")
    @PreAuthorize("@ss.hasPermission('trade:order:query')")
    public CommonResult<TradeOrderDetailRespVO> getByPickUpVerifyCode(@RequestParam("pickUpVerifyCode") String pickUpVerifyCode) {
        TradeOrderDO tradeOrder = tradeOrderUpdateService.getByPickUpVerifyCode(pickUpVerifyCode);
        return success(TradeOrderConvert.INSTANCE.convert2(tradeOrder, null));
    }

}
