# admin-api 契约（006）

管理端（`yudao-ui-admin-vue3`）本期新增的能力。所有路径以 `/admin-api` 为前缀，**只走 `/admin-api`**。

---

## 1. 开票申请分页

### `GET /trade/order-invoice/page`

| 参数 | 必填 | 说明 |
|---|---|---|
| `pageNo` / `pageSize` | 是 | 分页（照既有约定） |
| `status` | 否 | 申请状态：`0` 待开票 / `10` 已开票 / `20` 已作废。**不传 = 全部** |
| `orderNo` | 否 | 按订单号精确查（运营手上通常拿着订单号来找） |

```jsonc
{ "code": 0, "data": { "total": 12, "list": [
  { "id": 77, "orderId": 1001, "orderNo": "NO-20261015-0001",
    "userId": 88, "userNickname": "张三",          // 来自 MemberUserApi.getUserMap（既有做法）
    "type": 2, "typeName": "电子增值税专用发票",
    "status": 0, "statusName": "待开票",
    "amount": 7800,                                  // 开票金额快照，分（= 申请时的订单实付）
    "orderPayPrice": 7800,                           // 订单**当前**实付，分
    "orderRefundPrice": 3000,                        // 订单**当前**已退，分
    "titleName": "…", "taxNo": "…", "titleAddress": "…", "titlePhone": "…", "bankName": "…",
    "invoiceFileUrl": null, "issueTime": null, "issueUserId": null, "cancelTime": null,
    "createTime": 1790486340000 }                    // epoch ms
] } }
```

**两条服务端规则**：

1. **`status = 0`（待开票）时，必须只返回订单已支付的那些**（FR-169 / SC-059）——
   实现是分页 SQL **join `trade_order`** 并带 `pay_status = true` 条件（R30）。
   **不许"先分页再在内存里过滤"**：那样每页条数会不对。
2. `orderNo` 筛选走申请表上的冗余列（`trade_order_invoice.order_no`），不必 join 订单表。
3. 列表 MUST 带出订单**当前**的实付与已退金额（`orderPayPrice` / `orderRefundPrice`）。
   理由：`amount` 是**申请那一刻**的快照，**部分退款不会改它**（plan 风险 R2 / Q30 的口径）——
   运营在开具前要能看出"这笔已经退过一部分"，才能决定照开、还是先问财务
   （quickstart §六.3 走的就是这一步）。实现代价为零：这个分页**本来就 join 了 `trade_order`**

**不做**（明确）：按客户 / 金额 / 时间范围筛选（FR-168 只要求按状态；运营要找某笔就按订单号）。

---

## 2. 开具发票（上传文件并置为已开票）

### `PUT /trade/order-invoice/issue?id=<申请编号>&fileUrl=<文件地址>`

前端流程：`UploadFile` 组件先把文件传到 **既有** `/admin-api/infra/file/upload` 拿到 URL，
再把 URL 交给本接口（后端不接收文件流）。

```jsonc
{ "code": 0, "data": true, "msg": "" }
```

| 规则 | 依据 |
|---|---|
| **订单未支付 → 拒绝**（业务异常「订单还未支付，不能开具发票」） | FR-169 |
| **申请已作废 → 拒绝**（业务异常「该申请已作废」） | Q30 |
| **文件扩展名不在白名单（`pdf/jpg/jpeg/png`）→ 拒绝**，且**申请状态不变** | FR-171 / R31 |
| 成功：`status → 已开票(10)`、写 `invoice_file_url`、`issue_time = now`、`issue_user_id = 当前登录用户` | FR-170 |
| **已开票的再传一次 = 覆盖**（Q31）：仍走本接口，覆盖 URL 并刷新 `issue_time` / `issue_user_id`；**不留历史** | FR-172 / Q31 |
| 走**同一权限点**校验（见 §3），MUST NOT 只靠前端藏按钮 | FR-174 / Q29 |

**为什么"开具"和"上传"是同一个接口**：上传只是拿到 URL 的手段，**"开票"这个动作本身**才是
需要留痕（谁、何时）与校验状态的语义 —— 分成两个接口会出现"文件传上去了但状态没变"的中间态。

---

## 3. 菜单与权限点

新增菜单 `开票申请`（目录挂在 **商城 → 交易** 下，与「售后」同级；具体 `parent_id` 实施时按
`system_menu` 里 `name = '售后'` 那一行的 `parent_id` 查，**不写死**）：

| 菜单/按钮 | `type` | `permission` | 用处 |
|---|---|---|---|
| 开票申请 | 2（菜单） | `trade:order-invoice:query` | 菜单本身的可见性 |
| 上传发票 | 3（按钮） | `trade:order-invoice:issue` | 列表里的「开具」按钮 |

⚠️ **必须登记进 `system_menu`，否则连超管都看不到按钮**（004 的踩坑记录：`v-hasPermi` 只认
菜单表里出现过的 `permission` 字符串，而**后端对超管有旁路** → 表现为"接口能调、按钮不显示"）。
脚本照 `sql/mysql/004-filter-dimension-menus.sql` 的幂等写法（`INSERT ... SELECT ... WHERE NOT EXISTS`，
且目标表要**再包一层派生表** —— MySQL 不允许子查询里直接引用 INSERT 的目标表）。

**权限怎么授**（Q29）：不新造角色；运营在「角色管理」里给需要的角色勾上这两个权限点即可。
收窄权限不需要改代码。

---

## 4. 前端落点（管理端）

| 文件 | 内容 |
|---|---|
| `src/api/mall/trade/invoice/index.ts` | 两个函数 + 两个 VO 类型（照 `api/mall/trade/afterSale/index.ts`） |
| `src/views/mall/trade/invoice/index.vue` | 列表：`ContentWrap` + `el-table` + `Pagination` + 状态下拉筛选；操作列「开具 / 重新上传」用 `v-hasPermi="['trade:order-invoice:issue']"`；抬头五列在表格里可展开或走 `el-descriptions`（照售后详情的做法） |
| `src/views/mall/trade/invoice/InvoiceIssueForm.vue` | 弹窗：`Dialog` + `UploadFile`（`:file-type="['pdf','jpg','jpeg','png']"`、`:limit="1"`）+ `defineExpose({ open })` + `emit('success')`（照 `AfterSaleDisagreeForm.vue` + `FmsVoucherAttachmentForm.vue`） |

**本期不做**（明确）：从申请跳到订单的**页面跳转**只做"点击订单号 → 打开订单详情"（FR-173 已满足），
不做"在申请页里内嵌订单卡片"；订单详情页**不加**开票信息（运营要走申请菜单，那是 FR-167 要求它的理由）。
