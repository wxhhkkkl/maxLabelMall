# 手工验证清单（013 软件与驱动分发）

> 用最短路径证明 SC-126 ~ SC-139。
> **上线顺序**：`025` SQL → 后端 → 管理端/门面站 → 桌面端（R81，跨仓库）。

---

## 0. 前置

| 项 | 要求 |
|---|---|
| 数据库 | MySQL；已执行 `sql/mysql/025-software-distribution.sql` |
| 后端 | `java -jar yudao-server/target/yudao-server.jar`；**不要加 `--spring.profiles.active=local`** |
| 存储 | `infra_file_config` 存在启用中的 OSS 配置（线上为 `id=25`：`oss-cn-wulanchabu` / bucket `maxlabelmall` / 域名 `img.yuwangchenfa.com`） |
| 前端 | `storefront`、`yudao-ui-admin-vue3`、桌面端 `../maxlabel` |
| 租户 | 162；请求头 `tenant-id: 162` |

回查：

```sql
SELECT table_name FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name IN ('maxlabel_software_category','maxlabel_software','maxlabel_software_version');

SELECT permission FROM system_menu WHERE permission LIKE 'software:%' ORDER BY permission;
-- 期望 12 条
```

---

## 1. 存储与直传技术验证（R77，必须先做）

| # | 操作 | 预期 |
|---|---|---|
| 1.1 | 管理端对一个小文件调用 `POST /admin-api/software/version/presign` | 返回 `uploadUrl` / `objectKey` / `publicUrl` |
| 1.2 | 用返回的 `uploadUrl` 执行 `curl -X PUT --upload-file ./x.txt ...` | OSS 返回 200 |
| 1.3 | 匿名 `curl -I {publicUrl}` | 200，且 `Content-Length` 与文件一致 |
| 1.4 | 若 1.2 失败（签名不兼容） | 记录并切换到「服务端流式中转」方案（见 research R77），**不要**带着这个不确定性上线 |

---

## 2. 运营链路：分类 → 软件 → 版本 → 发布

> **SC-127 的计时口径**：从点击「新建软件」开始，到「发布并设为当前」成功为止；
> 分类若已存在，则从「新建软件」起算。目标 ≤ 5 分钟（含一次 5 MB 测试文件的上传）。

| # | 操作 | 预期 | 覆盖 |
|---|---|---|---|
| 2.1 | 后台新建分类「打印驱动」，排序 10 | 列表出现，顺序正确 | FR-330 |
| 2.2 | 在该分类下新建软件：标题「Zebra 驱动」、介绍、类型=打印驱动、勾选「服务支持页展示」 | 保存成功 | FR-331 |
| 2.3 | 为它上传一个约 5 MB 的测试文件，版本 `1.0.1`、平台 `windows`、架构 `x64` | 前端自动算出 sha512；登记后状态为**草稿** | FR-337 / FR-338 |
| 2.4 | 点「发布并设为当前」 | 状态变「已发布」，列表出现「当前发布」标记 | FR-345 |
| 2.5 | 再上传 `1.0.2` 并设为当前 | `1.0.2` 变当前，`1.0.1` 自动降为历史；**当前发布只有一个** | FR-352 |
| 2.6 | 对同一平台/架构再传一个 `1.0.2` | 被拒，提示版本号重复 | FR-333 |
| 2.7 | 传一个版本号为 `1.0` 的 | 被拒，提示格式非法 | FR-333 |
| 2.8 | 把草稿版本直接设为当前 | 被拒，提示「未发布版本不能设为当前」 | 状态机 |
| 2.9 | 两个浏览器同时把不同版本设为当前（或并发脚本 10 次） | 最终 `current_flag=1` 的行恒为 1；失败方收到可读提示 | SC-136 |

并发回查：

```sql
SELECT software_id, platform, arch, COUNT(*)
  FROM maxlabel_software_version
 WHERE current_flag = 1 AND deleted = 0
 GROUP BY software_id, platform, arch HAVING COUNT(*) > 1;
-- 期望 0 行
```

---

## 3. 服务支持页（门面站）

| # | 操作 | 预期 | 覆盖 |
|---|---|---|---|
| 3.1 | 未登录打开 `/support/software` | 按分类展示，含刚建的驱动 | FR-340 / FR-342 |
| 3.2 | 点进详情（URL 形如 `/support/software/10`） | 只看到 `1.0.2`（当前发布），看不到 `1.0.1` | FR-343 / SC-133 |
| 3.3 | 复制详情页 URL，换无痕窗口直接打开 | 正常展示，无需登录 | FR-354 / SC-138 |
| 3.4 | 点下载 | 浏览器直接从 `img.yuwangchenfa.com` 下载；应用服务器无该文件流量 | FR-335 / SC-129 |
| 3.5 | 把软件「服务支持页展示」取消勾选 | 列表与详情都不再出现（直接访问详情给出明确提示） | US50-AC2 |
| 3.6 | 停用其分类 | 该分类及其下软件都不出现 | US50-AC3 |

> 服务支持页**不提供**软件搜索（Q88）。

---

## 4. 桌面端更新（走 OSS，零服务器流量）

| # | 操作 | 预期 | 覆盖 |
|---|---|---|---|
| 4.1 | 后台建 `type=桌面端软件` 的软件，上传一个真实安装包并发布为当前 | 准备就绪 | — |
| 4.2 | `GET /app-api/software/update/check?currentVersion=1.0.22&platform=windows&arch=x64` | `hasUpdate=true`，含 `feedUrl` 与 `sha512` | FR-334 / Q89 |
| 4.3 | `GET /app-api/software/feed/windows/x64/latest.yml` | 返回 YAML，`version` 与当前发布一致，`path` 为 OSS 绝对直链 | R82 |
| 4.4 | 用 `currentVersion=1.0.23`（等于当前发布）再查 | `hasUpdate=false`，**无** `feedUrl` | Q90 |
| 4.5 | 用 `currentVersion=9.9.9`（高于当前发布，模拟回滚后） | `hasUpdate=false`，不提示降级 | FR-355 / SC-139 |
| 4.6 | 用 `platform=macos&arch=arm64`（无该平台版本） | `hasUpdate=false`，不返回 windows 的文件 | FR-350 |
| 4.7 | 用 `currentVersion=abc`（非法） | `hasUpdate=false`，不报 500 | Edge Case |
| 4.8 | 桌面端（已改用 check 的版本）触发更新 | 提示 → 下载（流量全在 `img.yuwangchenfa.com`）→ 校验 → 安装重启 | SC-126 / SC-131 |
| 4.9 | 更新期间在服务器上统计出网 | 安装包流量为 0；只有一次几 KB 的 check 请求 | SC-126 |
| 4.10 | 手工把 `sha512` 改错后再触发 | 客户端拒绝安装，当前版本仍可用 | SC-131 |

---

## 5. 下架、软删除与链接寿命

| # | 操作 | 预期 | 覆盖 |
|---|---|---|---|
| 5.1 | 下架 `1.0.1` | 从后台默认列表与对外入口消失，文件仍可下载 | FR-345 |
| 5.2 | 软删除某软件 | 后台与门面站都不再出现；**之前发出的下载地址仍可下载** | FR-353 / SC-137 |
| 5.3 | 记录一个已发布版本的 `file_url`，**30 天后**再访问 | 仍可直接下载 | SC-135 |
| 5.4 | 删除仍有软件的分类 | 被拒并提示先移走软件 | contracts/admin-api §1 |

---

## 6. 回归（必须逐项通过）

| # | 检查 | 预期 |
|---|---|---|
| 6.1 | 商品列表/详情/搜索 | 不受影响 |
| 6.2 | 管理端商品、订单、会员页面 | 不受影响 |
| 6.3 | 既有图片上传（`/admin-api/infra/file/upload` → `img.yuwangchenfa.com`） | 仍正常；`software/` 前缀不干扰图片目录 |
| 6.4 | 桌面端本地编辑、打印、授权判定 | 不受影响；更新失败不阻塞使用（FR-348 / FR-349） |

---

## 7. 显式不验证

| 项 | 原因 |
|---|---|
| 真实 CDN 限流/防盗链效果 | Q85 明确由运维配置，不属本功能范围 |
| 2 GB 以上超大包 | Assumptions 声明由运维另行协调 |
| 灰度、强制更新、增量差量 | 非目标 |
| macOS 公证与 Windows 代码签名 | 属桌面端发布流程（018），与分发模块无关 |
| FR-351「旧地址保留 90 天」 | 只在**存储迁移**时触发，属运维动作；本功能只保证「地址不随时间失效」，迁移期并存由运维按该条执行 |

---

## 8. 排障

| 现象 | 先查 |
|---|---|
| 上传返回 403 / 签名错误 | OSS 配置（`infra_file_config`）与预签名兼容性；见 §1.4 |
| 登记被拒「文件不存在」 | 服务端 HEAD `fileUrl` 的域名/DNS；确认文件确实 PUT 成功 |
| 发布后客户端仍提示无更新 | check 接口的 `platform/arch` 是否匹配；`current_flag` 是否为 1；软件 `type` 是否为 1、`status` 是否为 0 |
| 客户端拿到 404 的 yml | `feedUrl` 是否带尾斜杠；该平台是否真有当前发布 |
| 服务支持页不显示 | 分类是否启用、软件 `status`、`show_in_support` |
| 后台按钮不显示 | `system_menu` 是否登记了 `software:*` 权限点 |
