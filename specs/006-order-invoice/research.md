# 技术决策与研究（006）

Phase 0 产出。每条记：**决策 / 依据（查过代码，不是推测）/ 被否决的方案**。
编号续 005（005 排到 R24）。

---

## R25 —— 发票抬头簿放哪个模块？**trade，与开票申请同模块**

**决策**：两张新表**都放 `yudao-module-trade-server`**：`trade_order_invoice`（申请）、`trade_invoice_title`（抬头簿）。
C 端抬头簿接口走 `/app-api/trade/invoice-title/**`。

**依据**：

| 事实 | 出处 |
|---|---|
| 抬头簿的**形态**与收货地址簿完全同构（多条 + 默认 + 增删改） | `storefront/src/api/address.ts`（list/get/create/update/delete + get-default）、`types/address.ts` |
| 但地址在 order 侧是**必须服务端读取**的（发货要用），所以 trade 里注入了 `MemberAddressApi`；**抬头不需要** —— 前端提交的就是快照本身（R27） | `TradeOrderUpdateServiceImpl.java:223` 注入 `addressApi` 并 `getAddress(...)` |
| 票种枚举与抬头校验规则是**开票域**的概念，它们的归属地是 `trade-api`（与 `AfterSaleStatusEnum` 同处） | `yudao-module-trade-api/.../enums/aftersale/AfterSaleStatusEnum.java` |
| 跨模块调用在 yudao 里的代价是固定的：`xxxApi` 接口 + DTO 放被调方 `-api` 模块，调用方要在 `RpcConfiguration` 里注册 Feign 客户端 | `RpcConfiguration.java:35`；`MemberUserApi` / `MemberUserApiImpl` |

**为什么抬头簿不放进 member 模块**：放过去，member 侧要写一套"按票种校验抬头"的逻辑，
而它必须知道**票种**和**税号规则** —— 这两样都属于 trade（`trade-api` 的枚举）。
结果只有两个：① member 反向依赖 `trade-api`（一条新的、语义倒置的模块依赖）；② 在 member 里再抄一份票种枚举
（两个真相，且**必然**在改票种时漏改一处）。而把它放 trade 的收益是**一处校验、一处枚举、零跨模块依赖** ——
何况抬头在下单侧本来就不需要服务端读取。

**被否决的方案**：

- ❌ **放 member 模块**（照地址簿先例）—— 见上，代价是一条反向依赖或一份重复枚举，换来的只是"路径前缀更好看"。
- ❌ **放 member 但不校验**（只存字符串，校验全交给 trade）—— 那样抬头簿可以存进非法数据，
  而客户在结算页选中它时才会被拒，错误暴露在离输入最远的地方。

---

## R26 —— 数据模型：两张新表，还是往订单表加列？

**决策**：**新建 `trade_order_invoice`（1:1 挂订单）+ `trade_invoice_title`（抬头簿）**，
**`trade_order` 表一个字节不动**。

**依据**：

| 事实 | 出处 |
|---|---|
| `trade_order` 是全局最热的表（详情 / 列表 / 售后 / 统计都查它），本身已有 40+ 列 | `sql/mysql/mall-2026-04-18.sql:1473`；`TradeOrderDO.java` |
| 订单里**只有一个自由文本位 `remark`**，且 C 端从来不填 —— 承载不了"票种 + 5 个抬头字段 + 状态 + 文件"这一组结构化数据 | `AppTradeOrderCreateReqVO.java:13`；`storefront/src/types/order.ts:154` |
| 新表用 `trade_` 前缀、继承 `BaseDO`、带 `tenant_id`（既有 trade 表都这样） | `trade_after_sale` 的 DDL（`mall-2026-04-18.sql:1078-1113`）；`AfterSaleDO.java:26 extends BaseDO` |
| **FR-179 在子表上天然成立**："历史订单视为未申请"= 子表里没有对应行，**一行数据都不用补** | spec FR-179 |

**被否决的方案**：

- ❌ **往 `trade_order` 加 8 个可空列** —— 让每一条订单查询都多背一份与它无关的数据；
  且"无数据"与"未申请"要靠一堆 `NULL` 判断来表达，语义比"没有行"弱得多。
- ❌ **发票字段塞进 `remark` / 一个 JSON 列** —— 无法建索引、无法按状态筛选（后台列表的核心查询），
  也无法在数据库层面表达票种与状态的取值约束。
- ❌ **抬头与申请合到一张表**（每单存一份抬头、不建抬头簿）—— 直接违背 FR-164（客户要维护自己的抬头簿）与 US23。

---

## R27 —— 开票信息从哪来？**前端提交快照，服务端不读抬头簿**

**决策**：下单 / 补开时，前端把**选中的票种与抬头字段值**随请求提交；服务端**按票种校验后原样快照**
存进申请。服务端**不**根据 `titleId` 去查抬头簿。

**依据**：

| 事实 | 出处 |
|---|---|
| FR-158 本来就要求"下单那一刻的快照"，FR-166 / SC-061 要求"改 / 删抬头 MUST NOT 影响历史申请" | spec |
| 前端提交快照 → **历史订单不受抬头改动影响是"结构上不可能被影响"，而不是"靠代码小心"** | 本决策 |
| trade 读 member 的既有做法要 `xxxApi` + DTO + `RpcConfiguration` 注册 | `RpcConfiguration.java:35` |

**为什么这条重要**：如果服务端按 `titleId` 拷贝，就必须新增一条跨模块 RPC（trade → member），
而**收益为零** —— 拷贝的仍然是提交那一刻的值。多一条 RPC 只是多一个失败点和一个升级时要对账的文件。

**被否决的方案**：

- ❌ **传 `titleId`，服务端查 member 服务** —— 白加一条 RPC（见上）。
- ❌ **传 `titleId` 但只当引用存下来**（发票直接用抬头簿的当前值渲染）—— 直接违反 FR-158 / SC-061：
  客户改一次抬头，所有历史订单的发票信息跟着变。

---

## R28 —— 状态机与"作废"的触发点：**复用既有 `TradeOrderHandler` 扩展点**

**决策**：申请状态三态 `待开票(0) / 已开票(10) / 已作废(20)`。
**新增一个 `TradeInvoiceOrderHandler implements TradeOrderHandler`**，在 `afterCancelOrder` 里把该订单
**待开票**的申请置为已作废。**取消订单的三个方法一行都不改。**

**依据**（2026-10-15 查代码）：

| 事实 | 出处 |
|---|---|
| 订单更新服务里注入的是 **`List<TradeOrderHandler>`** —— 新增一个 Spring Bean 会被自动收进去 | `TradeOrderUpdateServiceImpl.java:104` |
| `TradeOrderHandler` 有 5 个 default 方法：`beforeOrderCreate` / `afterOrderCreate` / `afterPayOrder` / **`afterCancelOrder`** / `afterCancelOrderItem` | `service/order/handler/TradeOrderHandler.java:16-56` |
| **`afterCancelOrder` 在两条路径上都会触发**：`cancelOrder0`（第 638 行，C 端取消 + 系统超时取消都走它）与 `cancelOrderByAfterSale`（第 663 行，售后全额退款导致的取消） | `TradeOrderUpdateServiceImpl.java:638,663` |
| 售后"退款成功"的落地点是 `updateAfterSaleRefunded` → `updateOrderItemWhenAfterSaleSuccess` → 全额时 `cancelOrderByAfterSale` | `AfterSaleServiceImpl.java:384-416`；`TradeOrderUpdateServiceImpl.java:814-833` |
| 既有 handler 都在同一事务内被调用（`cancelOrder0` 的 `@Transactional` 之内） | `TradeOrderUpdateServiceImpl.java:553` |

**为什么不用 `afterPayOrder` 再给申请加一个"待支付"状态**：那等于把"订单是否已支付"这一事实
**在发票表里存了第二份**，而 `afterPayOrder` 若在某个支付路径上没被触发（后台改价、补支付、
异常回调），申请会**永远停在待支付**、运营在列表里看不到 —— **不报错的丢单**。
现在这个方案里"是否已支付"始终由订单裁决（R30 的 join），只有一份真相。

**被否决的方案**：

- ❌ **在 `cancelOrderByMember` / `cancelOrderBySystem` / `cancelOrderByAfterSale` 三处各插一行** ——
  撞的是同一件事的三个入口，将来上游加第四个取消入口时**必然漏掉**（而漏掉的后果是"票开给了一笔已取消的交易"，
  且不报错）。用扩展点则**新增入口自动覆盖**。
- ❌ **用 `afterCancelOrderItem`（按订单项）** —— 部分退款也会触发它，会把"只退了一件"误判成"订单取消"（见 R29）。

---

## R29 —— 部分退款怎么算？**不作废，也不动金额**

**决策**：**只有订单取消**（含着售后全额退款导致的取消）才作废待开票的申请。
**部分退款**（订单仍存在）**不改变申请状态、不改变申请金额**；后台列表把订单的实付 / 已退金额展示出来，
运营开具前自行判断。

**依据**：

- Q30 的口径是"订单**取消**或**退款完成**时，其**尚未开具**的开票申请作废"。
  部分退款**不取消订单**，把它一并算进"作废"会形成一个**走不出去的死结**：
  申请作废后，客户想就剩余金额开票 —— 而 FR-160 规定一个订单只能有一条申请，FR-182 又规定申请提交后不可改，
  于是**无路可走**（既不能改、也不能重新申请）。
- 发票金额是**申请提交那一刻**的实付金额快照（FR-161）：部分退款发生在申请之后时，
  严格做法是红冲重开 —— 而**红冲本期不做**（spec 的「本期不做」），所以口径只能是
  "不自动改、由运营在开具前判断"。

**这条要在 quickstart §4 里实测一次**（造一笔部分退款，看后台列表与客户端的表现符不符合预期），
它是本特性里**最可能被所有者改口径**的一处。

**被否决的方案**：

- ❌ **部分退款也作废** —— 见上，死结。
- ❌ **部分退款时自动把申请金额改成"实付 − 已退"** —— 直接违反 FR-182（申请提交后不可改），
  且客户看到自己的申请金额在背后变动而没有任何通知。

---

## R30 —— "未支付的申请不进待开票列表"怎么实现？**分页时 join 订单过滤**

**决策**：后台「待开票」列表的**分页查询**用自定义 SQL **join `trade_order`**，
条件为 `invoice.status = 待开票 AND order.pay_status = true`（订单已支付）。
发票状态机里**没有**"待支付"这一态。

**依据**：

- FR-169 / SC-059 要求待开票列表**只含已支付订单**的申请。
- 若先分页查申请再在前端/服务层过滤未支付的，**分页数量会不对**（过滤后某页可能不足 pageSize，
  甚至整页为空），这是列表类需求的经典坑。
- 自定义 SQL 在本仓库是既有做法：`yudao-module-trade-server/src/main/resources/mapper/brokerage/BrokerageUserMapper.xml`
  （trade 模块已有 XML mapper 先例）。

**被否决的方案**：

- ❌ **在申请上加一个 `paid` 字段，用 `afterPayOrder` 维护** —— 见 R28，两份真相 + 可能静默停在错误状态。
- ❌ **查完后在 Java 里过滤** —— 分页语义就错了（见上）。
- ❌ **后台不区分，靠运营自觉** —— 直接不满足 FR-169。

---

## R31 —— 发票文件：只存 URL，校验放服务端

**决策**：后台上传走**既有** `POST /admin-api/infra/file/upload`（管理端 `UploadFile` 组件），
申请上只存一个 `invoice_file_url`（单列，Q31 = 覆盖）。**服务端在"开具"接口里校验文件扩展名白名单**
（pdf / jpg / jpeg / png），不在白名单内直接拒且**状态不变**。

**依据**：

| 事实 | 出处 |
|---|---|
| 既有做法就是"文件先传到 infra，业务表只存 URL 字符串" | `AfterSaleDO.java:73-79`（`applyPicUrls` + `Jackson3TypeHandler`）；`ProductSpuDO.java:77-78` |
| 管理端 `UploadFile` 组件有 `fileType` / `fileSize` / `limit` 三个 prop，`limit=1` 时只回传一个 URL 字符串 | `components/UploadFile/src/UploadFile.vue` |
| 既有同类页面（FMS 凭证附件）就是"某状态 + 某权限下才可改附件" | `views/fms/voucher/list/FmsVoucherAttachmentForm.vue` |

**为什么服务端也要校验**：前端 `accept` 只是给浏览器用的过滤，**不是约束**；
直接把 URL 传给接口就能绕过。服务端白名单是唯一真实的那道门（与 FR-171 一致）。

**被否决的方案**：

- ❌ **新建一张文件表 / 用 `List<String>` 存多份** —— Q31 明确"只留最新一份"，那就是一个列。
- ❌ **只靠前端限制** —— 见上。
- ❌ **把文件内容存进数据库** —— 既有的文件服务就是干这个的（宪法原则 III）。

---

## R32 —— C 端接口形态：改既有两个接口，新增三个

**决策**：

| 接口 | 处理 |
|---|---|
| `GET /app-api/trade/invoice-title/list` | **新增**（抬头簿列表） |
| `POST/PUT/DELETE /app-api/trade/invoice-title/*` | **新增**（抬头簿增删改，照地址簿） |
| `POST /app-api/trade/order-invoice/create` | **新增**（订单详情补开，US20） |
| `POST /app-api/trade/order/create` | **改**：入参加可选 `invoice` 对象（同事务落库） |
| `GET /app-api/trade/order/get-detail` | **改**：响应加 `invoice` 对象 |
| `GET /app-api/trade/order/page` | **改**：每笔订单加 `invoiceStatus` |

**依据**：

- 补开必须是**独立接口**：订单已经存在，没有"重新下单"这条路可走。
- 补开接口的校验必须在服务端：**订单属于当前会员**（否则任何登录用户都能给别人的订单挂开票信息）、
  **订单已支付**（FR-163）、**尚无申请**（FR-160）。
- 订单详情/列表加字段的方向与 003 对 `/product/spu/page` 的处理一致：**只做加法**，
  不传/无数据时既有行为逐字不变。

**被否决的方案**：

- ❌ **订单详情另开一个"读发票"接口** —— 多一次往返，且前端要处理"订单拿到了、发票没拿到"的半失败态（详见 plan 的 Complexity Tracking）。
- ❌ **补开复用下单接口** —— 语义不同（下单要商品与地址），且会重新生成一笔交易。

---

## R33 —— 界面归属：抬头簿是**页面**，结算页是**卡片**

**决策**：C 端新增一个页面 `/account/invoice`（个人中心 → 账户资料组），形态照抄地址簿；
结算页在既有卡片流里加一张「发票」卡片；订单详情在既有卡片流里加一块发票区块。

**依据**：

| 事实 | 出处 |
|---|---|
| 地址簿就是"个人中心子页面 + 列表 + 弹层表单 + 设为默认" | `views/AddressView.vue`（199 行）+ `components/AddressFormDialog.vue`（424 行） |
| 结算页是一叠 `ml-card`（收货地址 / 商品清单 / 金额…），**没有备注框**，加一张卡片是纯新增 | `views/CheckoutView.vue` |
| 个人中心子页面要同时改三处：路由、侧栏、页面本身 | `router/index.ts:81-95`；`components/AccountSidebar.vue:23-39` |
| **新增无设计稿页面 MUST 登记进显式清单**，否则色表 / 断点 / 不自带顶栏页脚三条断言**管不到它** | `styles/design-consistency.spec.ts`（清单是显式的，不自动收录） |

**被否决的方案**：

- ❌ **抬头簿做成结算页里的弹层**（不下沉到个人中心）—— 直接不满足 FR-164（客户要能长期维护抬头）。
- ❌ **抬头簿做成独立一级路由**（如 `/invoice-titles`）—— 它天然属于个人中心，放外面会让"我的"体系多一个游离入口。

---

## R34 —— 后台列表：照抄售后单，不发明新版式

**决策**：`views/mall/trade/invoice/index.vue` 用 `ContentWrap` + `el-table` + `Pagination` + 状态筛选；
上传用弹窗 `InvoiceIssueForm.vue`（`Dialog` + `UploadFile` + `v-hasPermi`）。

**依据**：`views/mall/trade/afterSale/`（列表 + 详情 + 弹窗三件套）；
`views/fms/voucher/list/FmsVoucherAttachmentForm.vue`（"某状态 + 某权限下才可改附件"，与本特性同构）；
管理端组件靠 `unplugin-vue-components` 自动导入，新页面**不需要手写 import**。

**被否决的方案**：

- ❌ **把开票入口塞进订单详情页**（不加菜单）—— 违反 FR-167（要求独立菜单）与 ops 的实际用法
  （运营是"拿着一批申请去开票"，不是"翻到某笔订单"）。
- ❌ **用 BPM 审批流** —— 开票是执行动作而非审批，多一层流程只增加点击。

---

## R35 —— 后端测试怎么落地：trade 模块的**首批**单测

**决策**：在 `yudao-module-trade-server/src/test/java/.../service/invoice/` 下新建单测，
用 `BaseDbUnitTest`（H2 内存库）+ `@Import({InvoiceValidator.class, ...ServiceImpl.class})`，
并把两张新表补进 `src/test/resources/sql/create_tables.sql`。

**依据**（2026-10-15 查代码）：

| 事实 | 出处 |
|---|---|
| ⚠️ **trade-server 目前没有 `src/test/java`**（只有 test resources）—— 本特性是该模块第一次写单测 | `yudao-module-trade-server/src/test/` 目录内容 |
| 但**测试脚手架已经齐了**：`application-unit-test.yaml`（`jdbc:h2:mem:testdb;MODE=MYSQL`，`schema-locations: classpath:/sql/create_tables.sql`）、`sql/create_tables.sql`、`sql/clean.sql` | 同上 |
| 测试基类在 framework：`BaseDbUnitTest`（内存库集成式单测）/ `BaseMockitoUnitTest`（纯 Mockito） | `yudao-spring-boot-starter-test/.../ut/` |
| 可照抄的既有 Service 单测在 **product** 模块：`ProductFilterValueServiceImplTest`（`@Import` + `@Resource` + `@MockitoBean`） | `yudao-module-product-server/src/test/java/.../filter/` |
| 测试用的 H2 yaml 里配了 Redis（本地 16379）；trade 侧的 `TradeNoRedisDAO` 若被间接依赖，**Mock 掉即可** | `application-unit-test.yaml:23-27` |

**被否决的方案**：

- ❌ **只写 Mockito 单测不碰 H2** —— 本特性最该钉住的恰恰是**数据库层面的语义**
  （1:1 唯一、状态流转、自定义分页 join 的过滤），这些都只能在真 SQL 下验证。
- ❌ **不在 trade 模块写测试，靠手工清单** —— 与宪法原则 I 直接冲突（原则 I 的违规一律 CRITICAL）。

---

## R36 —— 不新增 e2e

**决策**：**不新增 e2e**（显式决定）。

**依据**：现有 e2e 跑在本地后端 + **租户 1** 的完整种子数据上（`playwright.config.ts` 的 webServer 覆盖
`VITE_TENANT_ID=1`）。发票链路的 e2e 前置要构造「下单 → 支付成功 → 后台开具 → 前台下载」四步，
其中"支付成功"需要走 mock 支付回调（`pay_channel` + pay 模块回调 + 90 秒启动等待），
**构造成本远高于它能带来的信息**；而申请、作废、快照三条核心语义已由服务端集成测试覆盖（R35）。

**被否决的方案**：❌ 为发票单独加一条 e2e —— 沿用 003 的同类判断（003 也因构造成本高于收益而明确不加）。

---

## R37 —— 客户怎么拿到发票文件：**直链 + 常驻求助路径**（`/speckit-analyze` 初步对照时补的一处）

**决策**：订单详情里给**对象存储的直链**（新标签打开），**链接旁常驻**一句
"文件打不开？请联系我们 / 010-88613986"。**不做**服务端代理下载。

**依据**（2026-10-15 实测）：

| 事实 | 出处 |
|---|---|
| 该文件域**不返回任何 CORS 头**（响应里没有 `access-control-allow-origin`） | `curl -H "Origin: …" https://img.yuwangchenfa.com/…` 实测 |
| 缺失文件返回 **404**（不是 200 的空文件） | 同上，用不存在的文件名实测 |
| → 于是浏览器里**读不到**这次请求的结果：`fetch` 被跨域策略挡下，`mode: 'no-cors'` 拿到 opaque 响应（**分不出 200 与 404**） | 浏览器同源策略的必然结果 |

**为什么这条要单独记**：spec 的 Edge Case 要求"下载失败 MUST 给出可读的提示与求助路径"。
**在这个文件域上，"当场提示失败"做不到** —— 不是没写代码，是浏览器不允许读结果。
所以本期只兑现该条的一半：**不静默**（新标签里客户看得见打不开）+ **有求助路径**（常驻文案）。
quickstart §五留了一步"把链接改坏看真实观感"，并把这条列进 plan 的风险 R10 供所有者复盘。

**被否决的方案**：

- ❌ **`fetch` 探测后再决定提示** —— 被跨域策略挡下（见上表），代码写了也不生效。
- ❌ **服务端代理下载**（`GET /app-api/trade/order-invoice/download?id=`，后端校验订单归属后转发文件）
  —— **技术上这才是正解**：失败可检测、提示可友好，还顺带把"链接公网可读"这条已接受的风险一起收掉。
  本期不做的理由只有一条：它是**新接口 + 文件流量过服务端**，超出了所有者已拍板的范围
  （spec 明确写了"本期不加签名 URL / 权限校验"）。**若所有者认为观感不可接受，这是首选出路。**
- ❌ **把文件转存到本站自己的域名下** —— 换汤不换药（既有的文件服务本来就是同一个对象存储），
  且要多一套同步逻辑。
