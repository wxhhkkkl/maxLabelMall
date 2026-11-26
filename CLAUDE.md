<!-- SPECKIT START -->
读当前计划之前，先读项目宪法（最高效力约定，含技术栈与四端约束）：
.specify/memory/constitution.md

For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
specs/019-product-browse-level/plan.md

企业席位（012）相关产物：
- specs/012-enterprise-seat-licensing/spec.md — 功能规格（Q65~Q74：企业 / 席位 / 有效期 / 可见性）
- specs/012-enterprise-seat-licensing/plan.md — 4 表 + 3 列 + 权益解析 + 企业中心
- specs/012-enterprise-seat-licensing/data-model.md — 企业 / 企业成员 / 授权批次 / 席位
- specs/012-enterprise-seat-licensing/contracts/app-api.md — C 端企业中心 / 企业商品 / 下单与发票
- specs/012-enterprise-seat-licensing/contracts/admin-api.md — 管理端企业 / 成员 / 席位 / 批次
- specs/012-enterprise-seat-licensing/quickstart.md — 手工验证清单与显式不验证项
  ⚠️ **上线顺序**：先按序执行 `sql/mysql/021-enterprise-seat-licensing.sql`、`022-enterprise-license-folded.sql`、
  `023-plan-edition.sql`（依赖 021 建的批次表）、`024-personal-subscription-order.sql`，再发后端，
  最后发管理端/门面站/桌面端（判据与回滚见 quickstart §10）
  📌 所有者追加口径（2026-10-26）：**复购数量 = 购买后的总席位数**，旧批次剩余时长折算进新批次（Q78）；
  **企业授权订单用户端不允许退款**，运营后台保留人工通道（Q79）
  📌 2026-10-27：**个人会员订阅订单用户端同样不允许退款**（后台保留人工通道）；纯虚拟订阅订单
  支付成功后直接置为「已完成」，历史已支付待发货订阅单由 `024` 迁移补成已完成（Q82）

当前特性（019 商品浏览等级：与会员等级关联）：
- specs/019-product-browse-level/{spec,plan,research,data-model,tasks,quickstart}.md
- specs/019-product-browse-level/contracts/{admin-api,app-api}.md
  ⚠️ **上线顺序**：先执行 `sql/mysql/041-product-browse-level.sql`（`product_spu` 增加
  `browse_level_type` / `browse_level_ids`），再发后端，最后发管理端；C 端无需发版（服务端过滤生效）
  📌 口径：`0` 公开（含未登录访客）/ `1` 全部登录会员 / `2` 指定等级会员（多选等级集合）；
  等级不足**完全隐藏**（列表 / 搜索 / 详情等同商品不存在，详情不泄露名称价格）；
  与企业专享、会员授权商品**独立叠加**，全部条件满足才可见；存量商品默认公开（`041` 前须先执行）
  📌 交易链路（加购 / 结算 / 下单）服务端二次校验，购物车/收藏/浏览历史只隐藏不删记录；
  营销活动（秒杀/拼团/积分/砍价）同样过滤；企业管理员不豁免等级条件

当前特性（020 授权看板：软件管理下看授权规模与赠送）：
- specs/020-license-dashboard/{spec,plan,research,data-model,tasks,quickstart}.md
- specs/020-license-dashboard/contracts/admin-api.md
  ⚠️ **上线顺序**：先执行 `sql/mysql/042-license-dashboard.sql`（新表 `member_license_grant` +
  两张表加 `license_grant_id` + 放宽来源列 + 菜单权限），再发后端，最后发管理端
  📌 口径：个人有效 = 订阅未过期；企业有效 = 启用 + 有有效批次（**不看席位是否分配**）；
  当年客户拆成「新获得 / 延续 / 合计」三个数，一律按 `create_time` 判年份，合计按客户去重
  📌 赠送：个人固定专业版、企业固定企业版；单次 ≤ 12 个月且赠送后剩余 ≤ 12 个月；
  必填理由、原子提交；`member:license:query` 与 `member:license:gift` 分开授
  📌 **2026-11-19 赠送撤回（Q164，推翻 Q154「不可撤销」）**：先执行
  `sql/mysql/050-license-grant-revoke.sql`（`member_license_grant` 加 `license_id` /
  `revoke_time` / `revoke_operator_id` / `revoke_reason`），再发后端，最后发管理端；
  权限**复用 `member:license:gift`**，无新菜单/按钮数据。
  口径：只能撤该对象**最近一笔**；个人删掉这笔赠送产生的订阅行，企业席位回减 `seats_total`、
  时长回退 `end_time` + `period_months`，赠送新建的批次整批撤回；批次上有**已分配席位**时拦下
  （不连带收回成员权益）；记录不删，只写 `revoke_*` 三列，赠送记录显示「已撤回」
  📌 **2026-11-17 赠送独立入口（Q162）**：看板顶部可对**全部个人与企业**发起赠送（按客户类型 /
  关键字筛选，不限是否已有授权），明细行内入口保留；无授权对象从当天起算；
  企业无有效批次时送席位须同时选时长（Q157）
  📌 **2026-11-17 看板卡片口径（Q163）**：个人卡片只显示**订阅会员**（席位会员仍在明细）；
  当年卡片按「新获得 / 延续」两组 × 个人会员数 / 企业席位数 / 合计 展示
  （合计单位不同，**不是客户数**）；企业席位 = 当年新增有效批次 `seats_total` 之和
  📌 **2026-11-16 起发版须所有者确认**（宪法原则 VI），任何上线动作不得自动执行

当前特性（021 客户服务：投诉咨询与在线维修）：
- specs/021-customer-service-repair/{spec,plan,research,data-model,tasks,quickstart}.md
- specs/021-customer-service-repair/contracts/{app-api,admin-api}.md
  ⚠️ **上线顺序**：先执行 `sql/mysql/043-customer-service.sql`（5 张表 + 菜单/权限），
  再执行 `sql/mysql/044-customer-service-refund.sql`（维修订单退款列 + 四个菜单 + 退款按钮），
  再执行 `sql/mysql/045-repair-order-complete.sql`（完成维修时间/操作人），
  再发后端，最后发管理端 / 门面站；回滚判据见 quickstart §六
  📌 **2026-11-17 维修订单退款**：仅已支付且未退款的全额退；**在线支付原路退**（pay 退款回调按
  `repair:` 前缀分流），**对公/线下确认由运营填原因后标记已退款**；权限 `trade:customer-service:refund`
  📌 **2026-11-17 管理端四个菜单**：客户服务 → 投诉咨询 / 在线维修 / 回收地址设置 / 维修订单
  📌 **2026-11-17 完成维修**：维修订单页手动「完成维修」（已寄出/维修中、已支付/无需支付、未退款），
  记录完成人 `complete_operator_id` 与 `complete_time`，维修申请置 50 已完成；已退款订单不可完成
  📌 **2026-11-17 提交上限**：同一客户最多同时保留 **10 条待处理投诉咨询**、**10 台在修维修申请**；
  投诉处理完成 / 维修完成或取消后可继续提交（服务端校验，管理员代建同样受限）
  📌 口径：个人中心「客户服务」含**投诉咨询 / 在线维修**；管理端「客户服务」可查看与代建工单、
  记录电话联系、开单（维修费 + 寄回运费）、确认对公收款、推进维修状态、维护回收地址
  📌 维修单是**独立服务单**，不进入「我的订单」；支付复用 pay，`merchantOrderId = repair:<id>`，
  支付回调在 `/trade/order/update-paid` 按前缀分流（回滚须前后端同时回滚）
  📌 客户**先支付（或 0 元/对公确认）再寄件**；回收地址由后台维护；客户侧不提供自助退款

当前特性（022 对公转账单导出 PDF：门面站仅前端）：
- specs/022-offline-pay-pdf/{spec,plan,research,data-model,tasks,quickstart}.md
- specs/022-offline-pay-pdf/contracts/pdf-content.md
  ⚠️ **上线顺序**：无 SQL、无后端改动，只发**门面站**；`storefront` 新增运行时依赖
  `jspdf` + `html2canvas`（点击导出时才动态加载，首屏体积不变）
  📌 口径：**所有未取消的对公转账订单**（`offlinePay` 非空且状态不是「已取消」）显示
  「导出对公转账单」；待收款时用于打款，确认收款后用于留档（Q165~Q168）
  📌 PDF 内容：收款账户（户名/账号/开户行 + 提示语）、商品清单（名称/规格/单价/数量/小计）、
  订单号/下单时间/**订单状态**/实付金额、金额明细（商品小计/促销优惠/运费/券/积分/会员优惠/应付）、
  收货信息、开票信息（已申请且未作废）、买家留言；A4 自动分页，长清单不截断
  📌 中文以画布图片渲染（文字不可复制），**商品图片不入 PDF**（跨域画布污染）；
  用户可控文本一律 HTML 转义（商品名/留言/抬头）
  📌 已取消订单不显示入口：后端 `waiting` 只看「是否确认收款/是否已付」，取消后仍可能为 true
  📌 回滚：回退门面站本次改动并重新发布；无数据库/后端回滚项

当前特性（023 代客开票：管理端代客户申请发票）：
- specs/023-agent-invoice/{spec,plan,research,data-model,tasks,quickstart}.md
- specs/023-agent-invoice/contracts/admin-api.md
  ⚠️ **上线顺序**：先发**后端**，再发**管理端**；**无 SQL、无新表/列、无新权限点**
  （接口与按钮复用 `trade:order:update`）
  📌 口径：代客下单表单可勾选「开具发票」（完整 006 字段，与订单同一事务落库）；
  后台订单详情可对**所有**已支付、未取消、实付 > 0、尚无发票的订单代客户补申请（Q170~Q172）
  📌 一单一条；企业授权订单必须企业抬头（订单快照 `maxlabel_org_id`）；0 元订单不能开票；
  后台代申请写订单日志「后台代客户申请发票」（`ADMIN_APPLY_INVOICE`）
  📌 修订 017 FR-465「不代客申请发票」；客户仍可在 C 端支付后自助补开
  📌 回滚：回退后端 + 管理端本次改动；已创建的发票申请属 006 既有数据，不需要清理

📌 **2026-11-18 订单中心一级目录 + 订单列表拆两菜单**（无新表、无加列）：
- 上线顺序：先执行 `sql/mysql/046-order-center-top-level.sql`（「订单中心」从 MALL 下移到根，
  path 改 `/trade`），再执行 `sql/mysql/047-order-list-split.sql`（订单列表改名「商品订单」+
  新增「授权订单」，角色授权跟随）→ 再发后端 → 最后发管理端；**管理员需重新登录**菜单才刷新
- 两个菜单共用 `mall/trade/order/index`，靠菜单 `component_name` 区分：`TradeOrder`（商品订单）/
  `TradeOrderLicense`（授权订单，默认标签「全部订单」）
- 口径：**授权订单 = `maxlabel_subscription = 1`（个人订阅）或 `maxlabel_org_id` 非空（企业授权）**，
  其余都是商品订单（含 BarTender 这类虚拟软件商品）；后端参数 `TradeOrderPageReqVO.license`，
  列表与状态统计共用同一实现。原来的「订单类型」「商品类型」筛选都已从列表页移除
- 会员详情里的订单列表（UserOrderList）仍有「订单类型」筛选，本次不动；导出仍只按时间范围

📌 **2026-11-18 商品关联驱动下载**：先执行 `sql/mysql/048-product-driver-link.sql`
（`product_spu.driver_software_id`）→ 再发后端 → 最后发管理端 / 门面站。
- 入口：商品编辑「其他设置 → 关联驱动下载」；选项来自「软件分发」的软件/驱动
  （`GET /software/info/simple-list`，权限按 **`product:spu:query`** 校验，商品运营不需要软件分发权限）
- 口径：关联的是**软件**而不是安装包；商品详情页下载该软件**最新的一条当前发布**
  （`current_flag = 1` 且 `status = 0`，按 `release_time DESC, id DESC` 取一条）。
  软件停用 / 没有当前发布 / 关联已删除 → 不显示入口（不报错）
- 按钮文案按类型：`type=2` 显示「下载驱动」、`type=1` 显示「下载软件」
- 取消关联必须显式置空：`updateById` 会忽略 null，后端用 `clearDriverSoftwareId` 的 `set null` 兜住

📌 **2026-11-18 商品内部编码**：先执行 `sql/mysql/049-product-internal-code.sql`
（`product_spu.internal_code`，选填）→ 再发后端 → 最后发管理端 / 门面站。
- 入口：商品编辑「基础设置 → 商品内部编码」；后台商品详情直接可见
- C 端商品详情「规格与库存」标签显示「商品编码：xxx」，没填不渲染（文案不叫“内部”）
- 纯展示字段，不参与搜索与下单

上一个特性（018 LabelShop 模板导入：后台按来源白名单迁移模板）：
- specs/018-labelshop-template-import/{spec,plan,research,data-model,tasks,quickstart}.md
- specs/018-labelshop-template-import/contracts/{admin-api,labelshop-upstream}.md
  ⚠️ **上线顺序**：先执行 `sql/mysql/038-labelshop-template-import.sql`（2 表 + 按钮权限），
  **已执行过旧版 038 的库再执行 `039-labelshop-member-import.sql`**（幂等补
  `subject_type` / `running_member_id` / 索引 / 会员按钮权限），再执行
  `040-personal-share-folders.sql`（016 文件夹加 `scope`/`owner_member_id`/`parent_id`，`org_id` 可空），
  再发后端，最后发管理端 / 桌面端；桌面端需发布 `maxlabel` 的 `.lsdx` 云端加载识别与个人文件夹 UI
  📌 **2026-11-16 个人云端模板与目录**：`Own_Type=0` 云端 JSON 导入时转 `.lsdx`（转换失败记失败项，
  不回退归档）；个人空间按来源分类路径建**多层稳定文件夹树**（同名路径复用），桌面端支持
  新建/改名/移动/删除；企业空间仍只建一个 `<时间>导入` 文件夹
  📌 口径：我的模板库 → OWNER 个人空间；组模板库 + 企业/组织模板 → 企业空间；公共 / 用户模板库不下载；
  每次导入新建 `<时间>导入` 文件夹；导入不受容量闸门限制；模板原样导入不清洗；企业/组织私有 JSON 仅归档
  📌 **2026-11-13 个人会员代导（Q145）**：仅供管理端运营在「会员详情 → 个人空间」发起，
  权限 `member:user:labelshop-import`；只读「我的模板库」写入该会员 016 个人空间，不建文件夹、不碰企业空间；
  C 端不开放；`038` 同一脚本已含 `subject_type` / `running_member_id` 与会员按钮权限；
  会员详情的「个人空间」页签已改为直接展示 016 个人空间内容（只读接口
  `/member/share-content/member-overview|member-page|member-get`，权限 `member:user:query`）
  📌 **2026-11-13 所有者取消强制 HTTPS**（原 FR-507 / Q137）：导入接口 HTTP 与 HTTPS 均可用，
  前端不再按协议禁用；nginx 无需为本功能额外配置 `X-Forwarded-Proto`
  📌 **2026-11-26 模板外链图片随模板导入（Q148~Q151）**：
  `<picture type="1" file="labelshop:\\codingv\...">` 按文件名从
  `https://cvsimg.oss-cn-beijing.aliyuncs.com/li/<name>` 下载（`yudao.maxlabel.labelshop.image-base-url` 可配），
  写入模板所在文件夹的 016 图片内容（`content_type=5`，宽高取 `srcwidth`/`srcheight` ÷ 100 的 mm）
  并内嵌为 `type=0` 正文 base64；
  原生 `img=` 内嵌图也转成正文（桌面端只读正文）。图片失败 / 非图片 / 超限只记警告、不阻断模板；
  同任务同文件夹同图只下载一次。**无 SQL、无新权限点**；OSS 上目前是缩略图（约 100dpi）
  📌 回滚：把按钮权限 `member:org:labelshop-import` 置为停用/删除即关闭入口（接口变 403）；
  已导入内容保留在 016 空间，2 张导入表保留供审计，不自动删除

当前特性（016 共享内容空间：个人 / 企业 / 平台）：
- specs/016-shared-content-space/{spec,plan,research,data-model,tasks,quickstart}.md
- specs/016-shared-content-space/contracts/{app-api,admin-api}.md
  ⚠️ **上线顺序**：先执行 `sql/mysql/033-shared-content-space.sql`（5 张新表 + 两张旧表迁移 +
  平台内容菜单）、`034-platform-content-menu-category.sql`（菜单归入「软件管理」+ 分类表），
  再发后端，最后发管理端 / 桌面端
  📌 口径：免费 10 MB / 专业 100 MB / 企业 300 MB 共享池；企业空间仅持席成员可用（管理员不豁免）；
  平台共享只允许后台管理人员上传（会员投稿关闭）；个人空间不可分享；存量模板全部计入配额

📌 **2026-11-05 订单分页 500 事故**：032「买家删除订单」的 `trade_order.user_deleted` 列必须先执行
`sql/mysql/032-order-user-delete.sql`；本次发版漏跑该脚本，导致 `/app-api/trade/order/page` 报
`Unknown column` → 500，已补跑。以后凡是后端引用了新列，必须把对应 ALTER 脚本纳入发版顺序。

📌 **2026-11-10 订单 ERP 开单状态**：`trade_order` 新增 `erp_created` / `erp_no`。
上线必须先执行 `sql/mysql/037-order-erp-status.sql`，再发后端，最后发管理端；
漏跑会让订单列表 / 详情报 `Unknown column 'erp_created'`。权限复用 `trade:order:update`，无需新增菜单。

当前特性（017 代客下单：管理端运营以客户名义下单）：
- specs/017-agent-order/{spec,plan,research,data-model,tasks,quickstart}.md
- specs/017-agent-order/contracts/{admin-api,app-api}.md
  ⚠️ **上线顺序**：先执行 `sql/mysql/036-agent-order.sql`（`trade_order.agent_user_id` + 来源字典
  「后台代客/40」+ 两个权限点 `trade:order:agent-create` / `trade:after-sale:agent-create`），
  再发后端，最后发管理端 / 门面站；漏跑 SQL 会导致订单列表 500（Unknown column）。
  📌 口径：微信扫码/支付宝扫码走真实在线支付 + 对公转账；排除印刷定制/门店自提；不用券不抵积分；
  所有代客订单禁客户自助取消/退款，运营后台可代发起售后；C 端显示「客服代下单」标识

上一个特性（015 发货前修改收货地址：仅待发货，从地址簿选）：
- specs/015-order-address-edit/{spec,plan,research,data-model,tasks,quickstart}.md
- specs/015-order-address-edit/contracts/app-api.md
  ⚠️ 无数据库变更；服务端新增 C 端 `PUT /trade/order/update-address`，门面站订单详情加入口

上一个特性（014 印刷定制：选材质 / 尺寸 / 工艺 → 报价 → 支付，后台可查定制要求）：
- specs/014-print-customization/{spec,plan,research,data-model,tasks,quickstart}.md
- specs/014-print-customization/contracts/{app-api,admin-api}.md
  ⚠️ **上线顺序**：先执行 `sql/mysql/027-print-customization.sql`（4 表 + `trade_order.customization`
  + 隐藏 SPU/SKU + 菜单），再发后端，最后发管理端 / 门面站

上一个特性（发票收据、邮箱与抬头顺序）：
- specs/011-invoice-receipt-email/{spec,plan,data-model,quickstart,tasks}.md
- specs/011-invoice-receipt-email/contracts/{app-api,admin-api}.md
  ⚠️ 上线顺序：先执行 `sql/mysql/020-invoice-receipt-email.sql`，再发后端，最后发两个前端

更早的特性（快递100 物流轨迹与地图）：
- specs/010-express-track/{spec,plan,research,data-model,quickstart,tasks}.md
- specs/010-express-track/contracts/{app-api,admin-api}.md
  ⚠️ `trade_delivery_express.code` **同时供微信小程序物流同步使用**，快递100 用新增的 `kd100_code`（R48）

更早的特性（桌面端接入商城会员体系 —— 跨仓库 `maxlabel` + `maxLabelMall`）：
- specs/009-maxlabel-member-integration/{spec,plan,data-model,quickstart,tasks}.md
- specs/009-maxlabel-member-integration/contracts/{app-api,admin-api}.md

更早的特性（对公转账：客户线下打款，财务确认收款）：
- specs/008-corporate-transfer/{spec,plan,research,data-model,quickstart,tasks}.md
- specs/008-corporate-transfer/contracts/{app-api,admin-api}.md — 确认收款 / 取消订单 / 筛选
  ⚠️ 三条口径已由所有者拍板（Q41~Q46）：自动取消筛选两处（R45）、`needsNoPay` 误判（R47）

更早的特性（虚拟商品：买软件不填收货地址）：
- specs/007-virtual-goods/{spec,plan,data-model,quickstart,tasks}.md
- specs/007-virtual-goods/contracts/{app-api,admin-api}.md — 结算响应加 `deliveryType`、字典加「虚拟商品」

更早的特性（订单开票：下单申请 · 后台开具上传 · 前台下载）：
- specs/006-order-invoice/{spec,plan,research,data-model,quickstart,tasks}.md
- specs/006-order-invoice/contracts/app-api.md · contracts/admin-api.md
  ⚠️ 三处口径**已由所有者拍板**（2026-10-15，Q32~Q34）：**申请与订单同一事务**（FR-181 收窄，plan R1）、
  **部分退款不作废也不改金额**（FR-184，plan R2）、**下载失败只做到"不静默 + 求助路径"**（Edge Cases，plan R10）
  📌 之后又叠加了 **Q35~Q39**：退款撤回**已开票**的申请、抬头加**个人**类型、抬头按**票种**分开管理、
  必填收敛为「**名称 + 税号**」—— 以 spec 的 Clarifications 为准（原文保留，只作来龙去脉）
  📌 **2026-11-05 专票字段拆分**：`trade_invoice_title` / `trade_order_invoice` 新增
  `bank_account`（银行账户）与 `bank_code`（行号）；`bank_name` 现在只表示开户行。
  上线必须先执行 `sql/mysql/035-invoice-bank-fields.sql`；历史 `bank_name` 中的合并文本不自动拆分。

更早的特性（微信扫码登录与首次登录补全资料）：
- specs/005-wechat-scan-login/{spec,plan,research,data-model,quickstart,tasks}.md
- specs/005-wechat-scan-login/contracts/app-api.md · contracts/admin-api.md — 管理端展示 + 前置配置阻塞

更早的特性（收货地址自动解析 / 商品分类筛选维度 / 商品详情与评价 / 门面站与后台打通）：
- specs/004-address-auto-parse/{spec,plan,research,data-model,quickstart,tasks}.md
- specs/004-address-auto-parse/contracts/address-parse.md — 客户端模块契约（无接口变更）
- specs/003-product-filter-dimensions/{spec,plan,data-model,research,quickstart,tasks}.md
- specs/003-product-filter-dimensions/contracts/{app-api,admin-api}.md — admin-api 是本仓库首份
- specs/002-product-detail-reviews-banner/{spec,plan,data-model,research,quickstart,tasks}.md
- specs/002-product-detail-reviews-banner/contracts/app-api.md

更早的特性（门面站与后台打通 —— 仍是本仓库的规格基线，编号 FR/SC/US 由它起算）：
- specs/001-mall-storefront-integration/{spec,plan,data-model,research,quickstart,tasks}.md
- specs/001-mall-storefront-integration/contracts/app-api.md
<!-- SPECKIT END -->

# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.