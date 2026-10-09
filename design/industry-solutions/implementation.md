# 行业方案页面实现

已替换原有行业方案页面，复用 DefaultLayout 的现有头部与底部。

- 总览：`/solutions`，兼容 `/solution`。
- 行业详情：`/solutions/:industry`，兼容 `/solution/:industry`。
- 10 个行业 ID：warehouse、manufacturing、apparel、medical、food、retail、crossborder、bakery、catering、semiconductor。
- 分类和搜索支持联合筛选；空结果支持重置；未知行业提供返回入口。
- 咨询入口进入现有 `/contact` 页面。

## 布局

内容最大宽 1200px，桌面主要区块上下留白 72px，手机 48px。总览行业卡片桌面三列、平板两列、手机单列。十张卡片全部显示时，桌面最后一张使用横向布局，保持收尾平衡。详情采用浅蓝背景承托痛点与方案思路，四步流程统一为清晰的蓝色图标，标签实物图放大并完整展示，减少图片内边距及零散装饰。Hero 使用宽幅场景与渐变衔接，大标题强化视觉层次。

正文使用 Vue/HTML 渲染，可编辑且响应式适配。Hero 保持大场景横幅，标签区域展示 30 张实物标签图片，方案亮点及相近行业配有插图或缩略图；食品保留痛点照片，烘焙已移除低分辨率痛点图。多数行业配图从设计稿中提取，烘焙及总览使用下文的新高清独立照片；完整设计稿不被生产页面加载。标签图内文字和条码是视觉示意，不用于实际打印或扫描。

## 高清资产与行业差异化调整（2026-10-09）

总览旧配图约 486×246px，烘焙痛点图只有约 80px 宽，设计稿裁图不足以支撑实际页面放大展示。已使用内置 image_gen 生成独立照片，并保留原尺寸导出 WebP：总览与烘焙 Banner 为 1672×941px，烘焙三张标签图为 1448×1086px。新的五张 PNG 母版保存在本目录 `*-v2.png`，生产图片保存在 `storefront/public/assets/solutions/*-v2.webp`；完整提示词记录在 [hd-asset-prompts.json](hd-asset-prompts.json)，导出脚本为 `export-hd-assets.py`。烘焙移除被放大的窄幅痛点图，产品信息标、生产时间标、陈列价签分别使用可颂包装、吐司包装、可颂价签照片。

子行业不再共用四张图标卡片。生产、仓储、跨境、半导体采用场景图与纵向交接链；烘焙、服装采用共享档案与三种输出分支；食品区分固定档案与批次变量；餐饮采用周转时间轴；医疗采用四个核对关口；零售采用按区域组织的换标任务。行业标题、说明和关注字段分别配置，视觉仍沿用同一套蓝色、字体、圆角与留白。原有头部、底部与 Hero 大图风格继续保留。

本轮生产构建、42 条相关单元测试及 3 条浏览器测试通过，新流程组件 ESLint 无警告。浏览器覆盖六种流程表达、10 个行业路由、高清标签图片加载及八个响应式宽度。截图前等待图片解码并返回页面顶部，避免懒加载空图及吸顶导航位置影响截图。

## 验证

- `pnpm build`：类型检查及生产构建。
- `pnpm test`：589 条单元测试通过。
- `pnpm exec playwright test --config playwright.solutions.config.ts`：3 条浏览器用例通过。覆盖搜索、分类、重置、导航、10 个详情页、原有头尾唯一性、未知行业、单数地址，以及 320/390/480/600/768/1100/1101/1440px 下的内容溢出检查。
- 新增页面和组件通过 ESLint。

## 实际页面截图

- [桌面总览](implemented-overview-desktop.png)
- [桌面仓储方案](implemented-warehouse-desktop.png)
- [桌面制造方案](implemented-manufacturing-desktop.png)
- [桌面烘焙方案](implemented-bakery-desktop.png)
- [手机总览](implemented-overview-mobile.png)
- [手机医药方案](implemented-medical-mobile.png)
- [手机烘焙方案](implemented-bakery-mobile.png)

本次完成代码与本地验证，未发布到线上。
