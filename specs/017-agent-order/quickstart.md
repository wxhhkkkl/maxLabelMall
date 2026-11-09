# Quickstart（017 代客下单）

> 本文件是**手工验收清单**与「显式不验证项」的落点，不替代自动化测试。
> 所有 `SC-xxx` 引用自 [spec.md](./spec.md)。

---

## 0. 前置

- 分支：`017-agent-order`
- 后端：`yudao-cloud`（单体聚合启动）
- 管理端：`yudao-cloud/yudao-ui/yudao-ui-admin-vue3`
- 门面站：`storefront`
- 数据库：MySQL 8.0（先备份 `trade_order`、`system_menu`、`system_dict_data`）

### 0.1 先执行 SQL（必须）

```bash
mysql -h <host> -u <user> -p <database> < yudao-cloud/sql/mysql/036-agent-order.sql
```

回查：

```sql
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order'
  AND COLUMN_NAME = 'agent_user_id';

SELECT id, name, permission, type, parent_id
FROM system_menu
WHERE permission IN ('trade:order:agent-create', 'trade:after-sale:agent-create');

SELECT label, value FROM system_dict_data
WHERE dict_type = 'terminal' AND value = '40';
```

预期：列存在且可空；两条权限各 1 行；字典「后台代客 / 40」1 行。

> ⚠️ **未执行 SQL 就发后端** → `Unknown column 'agent_user_id'` → 订单列表 500
> （与 032 事故同类，见 CLAUDE.md）。

---

## 1. 角色与权限（手工）

新建或复用一个测试角色，勾选：

| 权限 | 用途 |
|---|---|
| `trade:order:agent-create` | 代客下单入口与接口 |
| `trade:order:query` | 查看订单列表/详情 |
| `member:user:query` | 搜索客户、读取客户地址簿 |
| `product:spu:query` | 选择商品 |
| `trade:after-sale:agent-create` | 代客发起售后（可单独给客服主管/财务） |
| `trade:after-sale:query` | 查看售后单 |

再建一个**只有代客下单、没有代客售后**的角色，用于 SC-173 的负向验证。

---

## 2. 自动化测试

### 后端（先红后绿）

```bash
cd yudao-cloud
mvn -pl yudao-module-mall/yudao-module-trade-server -am test
```

> ⚠️ 必须带 `-am`，否则会读到本地仓库里过期的兄弟模块 jar（成片 `NoClassDefFound`）。
> 若出现 `VerifyError` / `ClassFormatError`，先清理 IDE 写进 `target/classes` 的脏 class。
> `pay` 模块有 5 个既有失败（`PayOrderServiceTest#testExpireOrder_*`），不要在本特性里修。

覆盖点：

- `AgentOrderServiceTest`：客户存在/禁用、商品范围（自提拒绝、隐藏定制 SKU 拒绝）、无地址拒绝、券积分强制关闭、支付渠道白名单；
- `TradeOrderUpdateServiceImplTest`：`createOrderByAdmin` 写归属/来源、会员下单不受影响、会员取消代客订单被拒；
- `AfterSaleServiceImplTest`：会员售后拒绝代客订单、后台代客售后允许且记录操作人、自助订单不可用后台入口；
- `TradeOrderMapperTest`：`agentCreated=true/false/null` 的列表与统计口径；
- `PayOrderApiImplTest`：`submitOrder` 的 DTO 转换（mock `PayOrderService`）。

### 管理端

```bash
cd yudao-cloud/yudao-ui/yudao-ui-admin-vue3
npm run test
```

覆盖点：代客下单页的步骤校验、请求体构造、支付方式/渠道映射、代下单人 fallback、
订单列表「代客下单」筛选与列展示。

### 门面站

```bash
cd storefront
npm run test
```

覆盖点：`canApplyRefund(..., agentCreated=true)` 返回 false、`canCancel` 返回 false、
订单列表/详情标识渲染、代客订单不出现取消/退款按钮。

---

## 3. 手工验收（对照 SC）

### V1 —— 创建实物代客订单（SC-161 / SC-162 / SC-165 / SC-172）

1. 管理端 → 商城 → 订单列表 → 「代客下单」；
2. 搜索客户手机号并选定（客户需已有地址）；
3. 选一件实物商品、数量 1，选择客户地址；预览价格；
4. 记下预览应付金额；提交，选择「微信扫码」或「支付宝扫码」；
5. 后台订单列表出现新订单：
   - 「代下单人」= 当前运营；
   - 「订单来源」= 后台代客；
   - 订单状态 = 待支付；
6. 用客户账号登录门面站 → 我的订单：
   - 列表出现该订单，显示「客服代下单」；
   - 详情显示「客服代下单」，无「取消订单」、无「申请退款」；
7. 用客户账号对同商品同数量自助结算（不选券/积分），应付金额与第 4 步一致。

### V2 —— 在线支付（SC-163 / SC-168）

1. 在代客下单页选择已配置的渠道（微信扫码或支付宝扫码）；
2. 后台出现二维码/链接；
3. 用测试账号扫码支付（真实金额，建议用最小金额商品）；
4. 支付回调后 10 秒内，后台与门面站都显示「待发货/已支付」；
5. 断开渠道配置（或选未配置的渠道）再试一次：
   - 返回可读错误；
   - 订单仍为待支付，不出现「已支付」。

> 若 `wx_native` / `alipay_pc` 只有一个在 `pay_channel` 启用，只验证已启用的那个；
> 另一个在 §6 标为「未验证 + 需先配置」。

### V3 —— 对公转账（SC-169）

1. 代客下单选择「对公转账」，提交；
2. 订单为「待收款」，后台不出现在线二维码；
3. 财务在订单详情「确认收款」，填流水号；
4. 订单进入「待发货」，确认人/时间留痕。

### V4 —— 虚拟商品与地址（SC-166）

1. 选纯虚拟商品（软件等），不选地址；预览/提交均不要求地址，运费为 0；
2. 选仅支持门店自提的商品 → 被拒绝，提示不支持代客下单；
3. 选印刷定制商品 → 入口不提供（隐藏 SPU 不在「出售中」列表）；直接传隐藏 SKU `990002` 调 `agent-create` → 返回 `AGENT_ORDER_CUSTOMIZATION_NOT_SUPPORTED`，**不是**通用价格错误；
4. 给没有地址的客户下实物单 → 返回 `AGENT_ORDER_ADDRESS_REQUIRED`，提示客户先在 C 端添加地址。

### V5 —— 企业授权商品（SC-166）

用企业 OWNER/ADMIN 的账号，代客下单企业授权商品：

- 成功时订单带企业快照，客户在 C 端可见；
- 用非 OWNER/ADMIN 的客户 → 拒绝，提示企业权限不足；
- 续费数量少于当前有效席位 → 拒绝（现有企业规则）。

> 若测试库没有可用企业/席位，标为「未验证」并说明。

### V6 —— 支付未完成后的恢复（Q130 / FR-472）

1. 创建代客订单但不支付，离开代客下单页；
2. 打开订单详情 → **不出现**「继续收款 / 重新发起支付」；
3. 运营在后台取消该未支付订单 → 成功；
4. 重新代客下单 → 新订单创建成功。

### V7 —— 客户侧守卫（SC-164）

1. 用客户账号打开代客订单详情 → 无「取消订单」、无「申请退款」；
2. 直接调用 `DELETE /app-api/trade/order/cancel?id=` → 返回 `1_011_012_006`；
3. 直接调用 `POST /app-api/trade/after-sale/create` → 返回 `1_011_012_007`；
4. 客户仍可正常支付该订单。

### V8 —— 后台代客售后（SC-171 / SC-173）

1. 对一笔已支付的代客订单，在订单详情点「代客发起售后」；
2. 选择订单项、退款方式、原因、金额，提交 → 售后单创建成功；
3. 客户在 C 端「我的售后」能看到该单及进度；
4. 财务按现有流程同意/退款：
   - 在线支付：按原渠道退回（测试环境可用小额）；
   - 对公/线下：财务线下打款，系统记录退款状态；
5. 用「只有代客下单、没有代客售后」的账号：
   - 订单详情不出现入口；
   - 直接调用 `POST /admin-api/trade/after-sale/agent-create` → 403/业务拒绝；
6. 用客户自助订单调用该后台入口 → 返回 `1_011_012_008`。

### V9 —— 留痕与筛选（SC-162 / SC-172）

1. 订单列表筛选「仅代客下单」→ 只出现代客订单；
2. 筛选「仅客户自助」→ 不出现代客订单；
3. 顶部统计数字与列表条数口径一致；
4. 订单详情的操作日志出现「运营代客下单」；
5. 后台用户改名后，历史订单显示新昵称；账号被删除/不存在时显示 `后台用户#{id}`；仅停用时仍显示昵称（可另加停用标记）；
6. 订单列表「订单来源」筛选「后台代客」→ 只出现代客订单。

### V10 —— 权限负向（SC-167）

用没有 `trade:order:agent-create` 的账号：

- 订单列表不出现「代客下单」按钮；
- 直接调用 `POST /admin-api/trade/order/agent-create` → 403。

---

## 4. 回归检查

- 客户自助下单：价格、券、积分、支付、取消、退款全部与改动前一致；
- 企业授权订单：下单校验、支付、席位分配不变；
- 对公转账：待收款、确认收款、取消不变；
- 后台订单列表/详情：既有字段与操作不变；
- 后台售后：既有会员售后审核/退款不变。

---

## 5. 部署顺序

1. `sql/mysql/036-agent-order.sql`（先查后执行；幂等）
2. 后端（trade + pay + common）
3. 管理端
4. 门面站

> 回滚：先回退前端/后端；`agent_user_id` **不建议 drop**（会丢失已产生的归属记录）。
> 临时关闭入口：停用两个 `system_menu` 权限点（`status=1`），或从角色里取消勾选。

---

## 6. 显式不验证项

| # | 不验证项 | 原因 / 处理 |
|---|---|---|
| N1 | 微信扫码 + 支付宝扫码**两条**真实资金链路都端到端验证 | 取决于商户号与 `pay_channel` 配置；通常只能验证已启用的那条，另一条只验证「未配置时的可读报错」 |
| N2 | 短信 / 站内信通知 | 本特性不新增通知（Q123 Assumption）；门面站无消息中心，短信模板也未绑定订单场景 |
| N3 | 企业授权 / 个人订阅代客订单退款后的席位与权益回滚 | 现有售后/支付副作用是否覆盖企业权益，需要财务与运营人工核对；本特性不新增权益回滚逻辑 |
| N4 | 跨租户代客下单 | 不支持；运营只能服务当前租户客户 |
| N5 | 代客下单的并发双击产生两笔订单 | 页面提交期禁用；未做服务端幂等键，极端并发下可能两笔，按现有后台取消处理 |
| N6 | 真实生产金额的支付/退款 | 只在测试账号/最小金额商品上验证，避免真实资金风险 |

---

## 7. SC 对照表

| SC | 验证方式 |
|---|---|
| SC-161 3 分钟内完成 | V1 计时 |
| SC-162 归属可查 | V1 / V9 |
| SC-163 支付后 10 秒内待发货 | V2 |
| SC-164 无自助退款入口、接口拒绝 | V7 |
| SC-165 与自助下单金额一致 | V1 第 7 步 |
| SC-166 不支持场景拒绝 | V4 / V5 |
| SC-167 无权限 0 次成功 | V10 |
| SC-168 渠道不可用不产生已支付 | V2 第 5 步 |
| SC-169 状态机一致 | V3 + 回归检查 |
| SC-170 创建请求不写客户资料/券/积分 | V1 + 检查请求体/客户数据（支付后的赠送沿用现有规则） |
| SC-171 售后可追溯发起人 | V8 |
| SC-172 来源可区分 | V1 / V9 |
| SC-173 代客售后权限分离 | V8 第 5 步 |

> **人工验证为主**：SC-161 / SC-163 / SC-165 / SC-167 / SC-169 / SC-170 依赖计时、真实支付、
> 权限负向、状态机一致性与「创建请求零写入」；自动化只覆盖可稳定断言的请求体、守卫与筛选。
> 不要把这些项当成「有单测就等于验过」。
