# Implementation Plan: 印刷定制下单

**Branch**: `014-print-customization` | **Date**: 2026-11-01
**Spec**: [spec.md](./spec.md)

## Summary

在商城门面站增加「印刷定制」页，把「选材质 → 填尺寸与数量 → 选工艺 → 传设计文件 → 实时报价 → 在线支付」搬上线，并把定制要求结构化地落进既有订单体系：

- **4 张新表**：材质、面积区间、全局计价参数、定制订单扩展表；`trade_order` 增加 `customization` 列用于筛选。
- **复用既有交易链路**：定制单仍是一笔 `trade_order`（支付、发票、运费、物流、售后全部沿用），定制要求放在 1:1 扩展表。
- **隐藏 SKU 承载订单项**：`trade_order_item.spu_id/sku_id` 是 NOT NULL，且库存处理器按 SKU 扣减；迁移预置一个不可售的「印刷定制服务」SPU/SKU。
- **服务端计价**：报价与下单两次都按当前参数重算；客户端提交的 `expectedAmount` 只用于「价格已变」的确认（Q101）。
- **后台**：订单列表可按「定制」筛选，详情展示规格、文件、价格构成与参数快照；材质 / 区间 / 参数有独立维护页。

## Technical Context

**Language/Version**: Java 17+（JDK 25 构建）/ Vue 3 + TypeScript
**Primary Dependencies**: yudao trade / product / pay / infra 既有模块；Redis（60 秒幂等）
**Storage**: MySQL（4 张新表 + `trade_order` 加一列）；设计文件存既有阿里云 OSS（`infra_file_config#25`）
**Testing**: JUnit 5（trade-server）+ Vitest（storefront / admin）
**Target Platform**: 服务端 Linux；storefront 与 admin-vue3 现代浏览器
**Project Type**: 四端单体聚合（本特性只动服务端 / 门面站 / 管理端）
**Performance Goals**: 报价 p95 < 200 ms（SC-145 的「1 秒内」留足余量）
**Constraints**: 定制单不参与优惠券 / 积分 / 会员折扣；不下推生产系统；文件沿用现有上传通道
**Scale/Scope**: 材质几十种、每材质几条区间；定制单量级与现有交易订单同量级

## Constitution Check

| 原则 | 结论 |
|---|---|
| **I. 测试先行** | ✅ 计价、区间校验、报价 / 建单、幂等、后台筛选均先写失败测试；DDL 与 H2 同步按既有例外处理 |
| **II. 先澄清** | ✅ Q91~Q104 已拍板（公式、单位、区间口径、双系数、状态、退款、上下限、加急、幂等、改价） |
| **III. 复用** | ✅ 订单 / 支付 / 发票 / 运费 / 物流 / 售后 / 文件上传全部复用；**金额由服务端裁决**，前端只展示 |
| **IV. 最小改动** | ✅ 不新建 maven 模块；只给 `trade_order` 加一列、给价格 BO 加一个字段 |
| **V. 可验证** | ✅ SC-140~SC-143 / SC-145 在 quickstart 有落点；**SC-144 显式声明不验证**（见「验证方式」） |

## 关键设计决策

完整论证见 [research.md](./research.md)（R87~R98）：

| # | 决策 |
|---|---|
| R87 | 定制单复用 `trade_order` + `trade_order_item`，新增 `trade_customization_order` 1:1 扩展表；不建第二套订单 |
| R88 | 订单项必须指向真实 SKU：迁移预置隐藏 SPU/SKU（库存给足、不进分类与搜索） |
| R89 | 计价挂在既有链路：`TradePriceCalculateReqBO.Item` 加服务端专用 `customPrice`；`createOrder` 检测到定制规格时先走定制计价 |
| R90 | 数量与金额落法：`count = 定制数量 N`、`price = ceil(总额 / N)`、`discount_price` 吸收取整差额，保证实付严格等于报价 |
| R91 | 定制域放 `trade-server`（`trade.customization` 包），不新建模块 |
| R92 | 下单时把材质、区间系数、初始价、加急系数写入 `param_snapshot` JSON；后台展示与重算都以快照为准 |
| R93 | 后台筛选用 `trade_order.customization` 位列 + 索引（与 008 的 offline 筛选同一做法：服务端翻译） |
| R94 | 60 秒幂等：Redis `SETNX trade:customization:submit:{userId}:{specHash}`，命中直接返回已建订单 |
| R95 | 报价不落库；建单必须带 `expectedAmount`，重算不一致返回 `CUSTOMIZATION_PRICE_CHANGED` + 新报价，不建单（Q101） |
| R96 | 定制单强制 `couponId = null`、`pointStatus = false`；隐藏 SKU 不进任何促销活动；运费仍走既有配送模板 |
| R97 | 设计文件复用 `/app-api/infra/file/upload`；服务端校验域名白名单 + 扩展名 + 数量（1~5），URL 与元数据存 JSON |
| R98 | 管理端菜单 / 权限幂等写入 `system_menu`；材质与区间同表单保存（区间必须连续），计价参数单独页 |

## 代码落点

```text
yudao-cloud/
├── sql/mysql/027-print-customization.sql                 # 4 表 + trade_order 列 + 隐藏 SPU/SKU + 菜单权限
├── yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/
│   ├── dal/dataobject/customization/{CustomizationMaterialDO,CustomizationAreaRuleDO,
│   │                                   CustomizationConfigDO,CustomizationOrderDO}.java
│   ├── dal/mysql/customization/{CustomizationMaterialMapper,CustomizationAreaRuleMapper,
│   │                            CustomizationConfigMapper,CustomizationOrderMapper}.java
│   ├── service/customization/{CustomizationMaterialService,CustomizationConfigService,
│   │                          CustomizationPriceService,CustomizationOrderService,
│   │                          CustomizationSpecValidator}.java
│   ├── controller/app/customization/AppCustomizationController.java
│   ├── controller/admin/customization/{AdminCustomizationMaterialController,
│   │                                   AdminCustomizationConfigController}.java
│   ├── controller/app/customization/vo/*.java
│   ├── controller/admin/customization/vo/*.java
│   ├── enums/customization/{CustomizationLogRecordConstants}.java
│   └── service/order/TradeOrderUpdateServiceImpl.java      # 只加「定制分支」与 customization 标记
└── yudao-ui/yudao-ui-admin-vue3/src/
    ├── api/mall/customization/index.ts
    └── views/mall/customization/{material/index.vue,config/index.vue}

storefront/src/
├── api/customization.ts
├── views/CustomizationView.vue                             # /customization
├── views/OrderDetailView.vue                               # 增加定制要求区块
└── router/index.ts                                         # 导航入口
```

**测试落点**

| 模块 | 测试 |
|---|---|
| trade-server | 计价（区间边界 / 最低收费 / 双系数 / 加急 / 四舍五入）、区间连续性校验、报价与建单金额一致、Q101 冲突、60 秒幂等、订单项快照、后台筛选 |
| storefront | `api/customization.spec.ts`、`CustomizationView.spec.ts`（校验 / 报价刷新 / 文件规则 / 提交） |
| admin | `api/mall/customization` 的接口封装与表单校验 |

## Complexity Tracking

无宪法违规。两处取舍仅作记录：

| 取舍 | 为什么 | 更简单的替代为何不可行 |
|---|---|---|
| 给 `trade_order` 加一列 | 列表筛选与 C 端详情都要判断「是不是定制单」，列 + 索引最直接 | 用子查询 `EXISTS` 会把 008 已经踩过的「列表与统计两处不一致」再引一遍 |
| 预置隐藏 SPU/SKU | 订单项与库存处理器都要求真实 SKU | 让 `spuId = 0` 需要改共享的库存处理器，回归面更大 |

## 验证方式

| 成功标准 | 验证方式 |
|---|---|
| SC-140 首次下单 ≤ 5 分钟 | quickstart 手工走查，记录耗时 |
| SC-141 重算一致率 100% | 单测覆盖计价 + quickstart 抽检 20 单 |
| SC-142 改价不影响历史单 | 单测：快照优先；quickstart 手工验证 |
| SC-143 后台 ≤ 2 次点击 | quickstart 手工走查 |
| SC-144 支付成功率 ≥ 95% | **本期不验证**：需要真实流量与较长观察窗口，上线后按周观察（宪法原则 V 显式声明） |
| SC-145 报价 1 秒内更新 | 前端单测 + 本地手工计时；上线后以接口 p95 < 200 ms 兜底 |

## 上线顺序

1. 执行 `sql/mysql/027-print-customization.sql`（4 表 + `trade_order` 列 + 隐藏 SPU/SKU + 菜单权限）
2. 发后端
3. 发管理端（材质 / 参数页）
4. 发门面站（定制页 + 订单详情区块）
