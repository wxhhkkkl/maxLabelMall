# 手工验证清单（012 企业客户与席位授权）

> 目标：用最短路径证明 SC-105 ~ SC-120 的端到端行为。
> **先跑 SQL，再发后端，最后发两个前端**（R74）。
> 支付统一用 **mock 渠道**，不要用真实支付（与 009 quickstart 一致）。

---

## 0. 前置

| 项 | 要求 |
|---|---|
| 数据库 | MySQL；已执行 `021-enterprise-seat-licensing.sql`、`022-enterprise-license-folded.sql` 与 `023-plan-edition.sql` |
| 后端 | `java -jar yudao-server/target/yudao-server.jar`（约 70 秒启动）；**不要加 `--spring.profiles.active=local`** |
| 前端 | `storefront`（Vite）、`yudao-ui-admin-vue3`、桌面端 `../maxlabel` |
| 租户 | 162；请求头 `tenant-id: 162` |
| 支付 | mock 渠道；确认租户 162 的 `pay_app` / `pay_channel` 已存在（见项目记忆） |

### 0.1 执行迁移

```bash
python scripts/apply_003_filter_dimension.py --file yudao-cloud/sql/mysql/021-enterprise-seat-licensing.sql
python scripts/apply_003_filter_dimension.py --file yudao-cloud/sql/mysql/022-enterprise-license-folded.sql
python scripts/apply_003_filter_dimension.py --file yudao-cloud/sql/mysql/023-plan-edition.sql
```

回查：

```sql
SELECT table_name FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name IN ('maxlabel_org','maxlabel_org_member','maxlabel_org_license','maxlabel_org_seat');

SELECT column_name FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND (table_name, column_name) IN
       (('maxlabel_subscription_plan','scope'),('product_spu','enterprise_only'),
        ('trade_order','maxlabel_org_id'),('maxlabel_org_license','folded_time'),
        ('maxlabel_subscription_plan','edition'),('member_subscription','edition'),
        ('maxlabel_org_license','edition'));

SELECT COUNT(*) FROM maxlabel_org_license WHERE folded_time IS NOT NULL;
```

预期：4 张表、7 列全部存在；`021`/`022`/`023` 重复执行不报错、不重复插入菜单。

档位口径（Q80）：`maxlabel_subscription_plan.edition` 存量回填为「个人=professional、
企业=enterprise」；`member_subscription` / `maxlabel_org_license` 按 `plan_id` 回填快照。

### 0.2 准备测试账号

| 账号 | 用途 |
|---|---|
| 会员 A（手机号 A） | 企业 OWNER；购买企业授权 |
| 会员 B（手机号 B） | 企业普通成员；被分配席位 |
| 会员 C（手机号 C） | 非企业会员；公开商城与下单拦截验证 |
| 运营账号 | 管理端；创建企业、代操作 |

---

## 1. 运营建档与商品配置

| # | 操作 | 预期 | 覆盖 |
|---|---|---|---|
| 1.1 | 运营在「会员中心 → 企业管理 → 新增」创建企业，OWNER 选会员 A | 企业启用；会员 A 的 C 端出现企业中心 | FR-288/314 |
| 1.2 | 运营按手机号把会员 B 加入企业 | 成员列表出现 B，角色普通成员 | FR-292 |
| 1.3 | 用未注册手机号加入 | 明确提示“该手机号尚未注册，请对方先注册” | FR-292 |
| 1.4 | 商品编辑新建/选择一个软件 SPU：配送方式**只勾虚拟商品**、SKU 价格 > 0、勾选「企业专享」 | 保存成功；公开商城列表/搜索不出现该商品 | FR-294/Q72/SC-118 |
| 1.5 | 会员授权商品页新增计划：该 SPU + 1 年 + `scope=企业` | 保存成功；计划列表显示“企业” | FR-294/295 |
| 1.6 | 再建一个普通个人计划（`scope=个人`，SPU 未勾企业专享） | 保存成功 | 回归 |
| 1.7 | 尝试给 `scope=企业` 计划绑定一个未勾企业专享的 SPU | 被拒绝并提示 | FR-294 |

---

## 2. 购买与发放

| # | 操作 | 预期 | 覆盖 |
|---|---|---|---|
| 2.1 | 会员 C（非企业）打开 `/enterprise/center` | 被引导登录或提示“未加入企业”；看不到企业商品 | Q72/FR-296 |
| 2.2 | 会员 C 直接访问企业商品详情 URL | 被拒绝或引导至企业授权说明 | Q72 |
| 2.3 | 会员 A 登录 → 企业中心 → 选择企业商品 → **数量填 2** → mock 支付成功 | 企业中心出现 1 条授权批次：2 席位、1 年、来源订单可见；可用席位 2 | SC-105/106 |
| 2.3a | 支付成功后打开订单详情 | 订单状态**直接为「已完成」**，没有发货入口 | Q77/FR-325 |
| 2.4 | 检查 `trade_order.maxlabel_org_id` | 该订单的企业 ID 已写入 | R62 |
| 2.5 | 结算页发票卡片 | **不出现个人抬头选项**；不勾选开票直接提交 → 下单成功 | Q73/SC-119 |
| 2.6 | 勾选开票，填企业抬头与税号后提交 | 下单成功；后台订单详情抬头为企业 | Q73 |
| 2.7 | 直接调接口传 `titleKind=2`（个人） | 被拒绝，`ENTERPRISE_ORDER_INVOICE_TITLE_REQUIRED` | Q73/SC-119 |
| 2.7 | 会员 B（普通成员）尝试购买企业商品 | 被拒绝 | FR-296 |
| 2.8 | 公开商城列表/搜索企业商品 | 不出现；个人商品与个人计划不受影响 | FR-294/回归 |

---

## 3. 席位分配与权益

| # | 操作 | 预期 | 覆盖 |
|---|---|---|---|
| 3.1 | 会员 A 在企业中心把席位分配给会员 B | 成功；已用 1 / 可用 1 | FR-301 |
| 3.2 | 再分配一次给 B | 幂等或明确提示“已有有效席位”，不重复占容量 | FR-303 |
| 3.3 | 把最后一个席位分配给会员 C（非本企业成员） | 被拒绝 | FR-305 |
| 3.4 | 用会员 A 给第三个成员分配（容量已满） | 被拒绝并提示“席位已用完” | FR-302/SC-109 |
| 3.5 | 会员 B 在桌面端刷新账户 / 打开 `/member/subscription/get` | `active=true`、`sourceType=2`、`orgName=企业名`、`edition=enterprise`、到期日正确；桌面端左栏显示「企业版」与「企业授权 · 企业名」 | SC-108 |
| 3.6 | 会员 B 上传一个 `.msdx` 模板 | 上传成功（企业席位与个人订阅等价） | Q69/SC-116 |
| 3.7 | 会员 B 同时有个人订阅且更晚到期 | 到期日显示两者较晚者，`sourceType=1` | Q67/SC-112 |
| 3.8 | 会员 A 撤销 B 的席位 | 席位释放、可用 +1 | FR-304 |
| 3.9 | 会员 B 再上传模板 | 403；若 B 有个人订阅则仍可上传 | SC-110 |
| 3.10 | 会员 B 已上传的模板与打印记录 | 仍归 B 本人、仍可查看 | FR-308/SC-115 |

> 桌面端验证：`MallAccountDialog` 显示「企业授权（XX 公司）」；`entitlementService` 不因来源改变行为。

---

## 4. 成员、角色与 OWNER

| # | 操作 | 预期 | 覆盖 |
|---|---|---|---|
| 4.1 | 会员 A 把 B 设为 ADMIN | B 能进入企业中心并分配席位 | FR-291/292 |
| 4.2 | B（ADMIN）尝试取消 A 的 OWNER / 转交 | 被拒绝（只有 OWNER 能转交） | FR-291 |
| 4.3 | A 把 OWNER 转交给 B | B 成为 OWNER，A 降为 ADMIN；企业仍只有一个 OWNER | Q74/SC-120 |
| 4.4 | A（ADMIN）尝试退出企业 | 允许；席位释放；个人订阅不受影响 | FR-293/308 |
| 4.5 | B（OWNER）尝试直接退出 | 被拒绝，提示先转交 | FR-291 |
| 4.6 | 把会员 C 加入另一个企业 | 被拒绝，提示先退出原企业 | FR-290 |
| 4.7 | 运营在后台代转 OWNER | 效果与自助一致，操作记录可见 | FR-314/318 |

---

## 5. 续费、过期与停用

| # | 操作 | 预期 | 覆盖 |
|---|---|---|---|
| 5.1 | 企业授权未到期、原有 1 席位还剩约 10 个月时，再买 2 席位 × 1 年 | 新批次**从今天起算**，2 席位、约 17 个月后到期（折算，不再等到明年才生效）；旧批次显示「已折算」 | Q78/FR-298/SC-124 |
| 5.1a | 折算后核对企业中心「席位容量 / 有效至」 | 共 2 席、有效至新批次到期日；已折算批次不计入容量与到期日 | Q78/SC-124 |
| 5.2 | 连续纯续费 3 次（每次数量 = 当前席位） | 总到期日 = 初始到期日 + 3 个周期，误差 0 天；数量默认/最小 = 当前席位；旧批次逐条变「已折算」 | SC-107/121 |
| 5.2a | 当前 2 个席位时，把购买数量改成 1 并点购买 | 前端拦截提示「不能少于当前 2 个」；直接调接口也被后端拒绝 | Q75/SC-121 |
| 5.3 | 手工把一条旧批次的 `end_time` 改到过去，保留另一条未过期 | 已分配席位**继续有效**，可用容量按剩余批次计算 | Q71/R64 |
| 5.4 | 把所有批次的 `end_time` 改到过去 | 企业来源权益全部失效；席位记录仍在；模板/打印记录可查 | FR-307/SC-115 |
| 5.5 | 再购买一个批次 | 之前分配的席位**自动恢复**，无需重新分配 | Q71 |
| 5.6 | 运营停用企业 | 所有企业来源权益失效；成员账号/订单/模板/打印记录保留 | FR-317 |
| 5.7 | 重新启用企业（批次未过期） | 席位恢复 | Assumptions |
| 5.8 | **运营后台**退款一笔企业订单 | 批次显示「已退款」；**不再计入**到期日、席位容量与续费起点；已分配席位不自动撤销 | Q76/FR-299 |
| 5.8a | 退款后再续费 | 续费起点是**未退款批次**的原到期日，不会跳到退款批次的到期日 | Q76/SC-123 |
| 5.9 | C 端打开企业授权订单详情 | **没有**「申请退款」入口；订单详情响应 `enterprise=true` | Q79/FR-327/SC-125 |
| 5.9a | 绕过前端直接 `POST /app-api/trade/after-sale/create`（带该订单的订单项） | 返回 `ENTERPRISE_ORDER_REFUND_NOT_SUPPORTED`；不产生售后单 | Q79/SC-125 |
| 5.9b | 运营后台仍可对该企业订单做售后退款 | 可正常退款；批次打「已退款」标记（人工通道保留） | Q79 |

---

## 6. 隐私与可见性

| # | 操作 | 预期 | 覆盖 |
|---|---|---|---|
| 6.1 | 企业管理员打开企业中心 | 只看到成员标识、角色、席位状态、企业授权批次 | FR-320 |
| 6.2 | 企业管理员尝试访问成员的模板/打印接口 | 被拒绝 | FR-319/SC-113 |
| 6.3 | 运营在会员详情查看 B | 只读显示“所属企业”；模板/打印页签仍按既有权限 | FR-321 |
| 6.4 | 会员 B 被移出企业后 | 企业管理员看不到其模板/打印数据 | US46 |

---

## 7. 回归（必须逐项通过）

| # | 检查 | 预期 |
|---|---|---|
| 7.1 | 纯个人订单：下单、支付、发放订阅、续费、退款 | 与 009 完全一致 |
| 7.2 | 公开商城个人商品列表/搜索/详情 | 与今天一致；仅企业商品被排除 |
| 7.3 | 桌面端未登录本地编辑/打印 | 不受影响 |
| 7.4 | 既有授权码用户 | 本地可用性与授权展示不变 |
| 7.5 | 个人订阅的云端写入闸门 | 不受企业表与 `scope` 影响 |

---

## 8. 显式不验证

| 项 | 原因 |
|---|---|
| 真实支付渠道扣款 | 涉及资金；用 mock 渠道验证回调链路 |
| 高并发支付回调 / 席位争抢压测 | 幂等与并发由唯一键 + 单测覆盖；压测不在本期 |
| 生产环境角色权限分配 | 需人工在「角色管理」勾选新权限（SC 之外的运维项） |
| 小程序 / App 端 | 本期只覆盖门面站、管理端与桌面端 |
| 企业自助注册 / 资质审核 | 明确不在范围（一期运营建档） |
| 桌面端一次刷新内反映企业权益变更 | 由 §3.5 手工验证；本期不做自动化计时断言 |

---

## 9. 排障

| 现象 | 先查 |
|---|---|
| 支付成功但企业批次没出现 | 订单 `maxlabel_org_id` 是否为空；计划 `scope` 是否为 2；`pay_notify_task` 是否回调成功；`source_order_item_id` 是否已存在 |
| 成员端仍显示“未订阅” | `/member/subscription/get` 的 `sourceType/orgName`；席位 `status=0`；企业 `status=0`；批次 `end_time > NOW()` |
| 上传 403 | 解析后的 `active` 是否为 true；席位是否被撤销；企业是否停用 |
| 企业商品仍出现在公开商城 | SPU `enterprise_only` 是否为 1；列表接口是否加了过滤；缓存是否未刷新 |
| 后台按钮不显示 | 权限串是否登记成 `system_menu` 行；角色是否勾选（超管也需要菜单行存在） |
| 企业订单下单被拒 | 买家是否 OWNER/ADMIN；`invoice.titleKind` 是否为 1；税号是否合法 |
