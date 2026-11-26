# Research：对公转账订单导出 PDF 凭证

**日期**：2026-11-25 | **计划**：[plan.md](./plan.md)

本特性没有 `NEEDS CLARIFICATION`（Q165~Q167 已拍板）。以下记录实现层面的关键选型。

## R1 PDF 生成管线

- **Decision**：`build model → render A4 HTML → html2canvas(canvas) → jsPDF(addImage) → save`
- **Rationale**：订单详情数据已全部在前端；中文以浏览器字体渲染进画布，无需嵌入 CJK 字体；
  管线短、依赖少、可完全在 C 端完成，不新增接口与鉴权面。
- **Alternatives**：
  - 浏览器打印另存 PDF：零依赖，但不是「下载文件」，且打印对话框各异（Q165 未选）。
  - 后端生成（OpenPDF/iText + 中文字体）：文字可搜索，但需新 Maven 依赖 + 数 MB 字体 +
    新接口 + 后端发版，改动面远大于收益（Q165 未选）。
  - `html2pdf.js`：只是上述两个库的封装，多一层黑盒，分页控制更差。

## R2 动态加载依赖

- **Decision**：导出函数内部 `await import('jspdf')` / `await import('html2canvas')`
- **Rationale**：两个库只服务一个低频按钮；动态加载使他们成为独立 chunk，首屏体积不变。
- **Alternatives**：静态 import —— 首屏白增数百 KB，不划算。

## R3 A4 版式与分页

- **Decision**：离屏容器宽 **794px**（≈A4 96dpi）；`html2canvas({ scale: 2 })`；
  PDF 页边距 **10mm**，内容宽 **190mm**、高 **277mm**；图片高度 = `190 × canvasH / canvasW`；
  超过 277mm 时循环 `addPage()`，每次把同一张图按负偏移写入（标准切片法）。
- **Rationale**：固定 794px 保证排版与屏宽无关；scale=2 让打印文字足够清晰；
  分页只切不缩，商品不丢（SC-220）。
- **Alternatives**：按页拆 DOM（复杂、易断行）；整图缩放到一页（长清单会缩到不可读）。

## R4 HTML 转义

- **Decision**：所有用户可控文本（商品名、规格、留言、发票抬头、税号、地址）写入 HTML 前
  统一 `escapeHtml`（`& < > " '`）。
- **Rationale**：PDF 容器用 `innerHTML` 注入；商品名与留言是用户输入，不转义会破坏 DOM
  或执行脚本（FR-596 / SC-221）。转义而非 sanitize，因为这里只需要「按文本显示」。
- **Alternatives**：全部用 `textContent` 逐节点构建 —— 安全但模板代码冗长；
  DOMPurify 清洗 —— 过度且可能改变文本。

## R5 文件名

- **Decision**：`对公转账单_<订单号>.pdf`；订单号过一遍非法字符清理（`\ / : * ? " < > |` → 去掉）。
- **Rationale**：财务归档按订单号检索；避免浏览器/系统拒绝非法文件名。

## R6 入口位置与可见性

- **Decision**：入口渲染在订单详情「待收款」区块之后的操作条；条件为 `order.offlinePay`
  非空**且订单未取消**（不是 `waiting`）。待收款与确认收款后都可见（US71 / US72）；
  已取消订单不显示（Q168 —— 取消后 `waiting` 仍可能为 `true`）。
- **Rationale**：不改既有 `#offlinePay` 区块的 `v-if="offlineWaiting"`（008 口径与既有测试
  依赖它）；`offlinePay` 非空正是「这单是对公转账」的权威判据（E1）。
- **Alternatives**：把入口塞进 `#offlinePay` 卡片 —— 确认收款后卡片消失，违背 US72。

## R7 生成中 / 失败状态

- **Decision**：`exporting` 布尔：生成期间按钮禁用并显示「生成中…」；失败 `message` 提示
  「生成 PDF 失败，请稍后重试」；`finally` 复位。离屏容器在 `finally` 中移除。
- **Rationale**：满足 FR-595；避免残留 DOM 与重复点击。

## R8 商品图片不进入 PDF

- **Decision**：PDF 模板不含 `<img>`。
- **Rationale**：订单项图片来自上传域名，`html2canvas` 读取跨域图片会污染画布，
  `toDataURL` 直接抛错（E6）。文字信息已满足凭证用途；FR 未要求图片。

## R9 金额与时间格式

- **Decision**：金额复用 `utils/money.ts#formatYuan`（分 → `¥12.90`）；时间复用
  `utils/time.ts#formatDateTime`（epoch 毫秒）。
- **Rationale**：与订单详情页显示一致（FR-598）；避免第二套格式化规则。

## R10 测试策略

- **Decision**：
  - 纯函数单测：内容模型字段、缺失值「—」、金额/时间格式、HTML 转义、文件名清理、分页参数；
  - 组件测试：`OrderDetailView` 中非对公单不出现入口、待收款/已收款对公单出现入口；
  - 导出编排测试：打桩 `jspdf` / `html2canvas`，断言 `save` 文件名与 `addPage` 次数；
  - 不新增 Playwright e2e（下载行为在无头环境不可靠），由 quickstart 手工验证。
- **Rationale**：符合宪法原则 I 与 V；把浏览器能力隔离在薄编排层，纯逻辑可测。
