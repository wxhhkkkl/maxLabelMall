# Quickstart: 共享内容空间（016）

> 目标：能按本清单独立验证 SC-149~SC-160。
> 约定：服务端本地单体启动，`java -jar yudao-server/target/yudao-server.jar`（约 70 秒）；
> **不要加 `--spring.profiles.active=local`**（见项目记忆）。
> 测试命令一律带 `-am`，并先清掉 IDE 写进 `target/classes` 的脏 class。

## 1. 前置条件

- 012 的企业/席位/授权已经可跑：能建档企业、分配席位、发放批次。
- 准备四个账号：
  | 账号 | 用途 |
  |---|---|
  | A | 免费（standard）个人会员 |
  | B | 专业版（professional）个人会员 |
  | C | 企业 OWNER 或 ADMIN，**且已分配席位** |
  | D | 企业 MEMBER，**且已分配席位** |
  | E | 同企业 MEMBER，**未分配席位** |
- 企业授权在有效期内；准备一个可上传的 `.msdx`、`.txt`、`.csv`、`.xlsx`、`.png`。

## 2. 数据库

```bash
mysql -h <host> -u <user> -p <db> < yudao-cloud/sql/mysql/033-shared-content-space.sql
mysql -h <host> -u <user> -p <db> < yudao-cloud/sql/mysql/034-platform-content-menu-category.sql
```

回查：

```sql
SHOW TABLES LIKE 'maxlabel_share%';
SELECT COUNT(*) FROM maxlabel_share_content;          -- 应 >= 旧模板数
SELECT COUNT(*) FROM maxlabel_platform_content;       -- 应 = 旧已通过共享模板数
SELECT scope, owner_id, used_bytes FROM maxlabel_share_space ORDER BY scope, owner_id;
SELECT id, name, permission FROM system_menu
 WHERE permission LIKE 'member:platform-content-%' ORDER BY id;
-- 旧表只读：迁移前后行数应一致
SELECT COUNT(*) FROM maxlabel_label_template;
SELECT COUNT(*) FROM maxlabel_shared_template;
```

迁移幂等：重复执行脚本，`maxlabel_share_content` / `maxlabel_platform_content` 行数不变。

## 3. 服务端自动化

```bash
# 先清脏 class（IDE 运行过测试时必做）
mvn -pl yudao-module-member/yudao-module-member-server -am clean test \
  -Dtest='ShareSpaceServiceImplTest,ShareFolderServiceImplTest,ShareContentServiceImplTest,PlatformContentServiceImplTest,*ControllerTest'
```

必须覆盖的用例（测试先行，先红后绿）：

- 个人：10 MB / 100 MB 边界；超限拒绝；删除后立即释放；存量模板计入；
- 企业：席位撤销 → 拒绝；授权过期 → 只读；无席位管理员 → 拒绝；
- 文件夹：无权限不可见；管理员绕过；非空不可删；根目录上传拒绝；
- 平台：普通会员调用管理端接口 403/拒绝；下架后 C 端不可见；
- 兼容：旧 `/cloud-template/*` 返回结构不变；`submit-share` 返回关闭错误。

## 4. 手工验收（对应成功标准）

### 4.1 个人空间（SC-149 / SC-150 / SC-153 / SC-155 / SC-160）

1. A 登录桌面端 → 个人页签显示「10 MB」；上传 9.9 MB 的 txt → 成功。
2. 再上传 1 MB → 拒绝，提示已用 / 上限 / 剩余；列表中没有半截文件。
3. 删除一个文件（出现二次确认）→ 立刻可上传；已用容量立即减少；已删内容不可恢复。
4. B 登录 → 个人页签显示「100 MB」；A 的内容对 B **不可见**，直接按 id 取也失败。
5. 把 B 的订阅置为过期 → B 变成 10 MB；已有内容仍可下载/删除，新增上传被拒。
6. 用 A 的历史云端模板（旧表）登录 → 仍能在个人页签看到并下载；其体积计入已用。

### 4.2 企业空间与席位（SC-151 / SC-154 / SC-157）

1. C 创建「财务」「门店」两个文件夹，只把「财务」授权给 D。
2. D 登录 → 只看到「财务」；直接请求「门店」→ 失败且提示不暴露存在性。
3. D 在「财务」上传文件 → C 能看到并删除；D 删除 C 上传的文件 → 被拒。
4. E（无席位）登录 → 看不到企业页签；直接调用 → `MEMBER_SHARE_SEAT_REQUIRED`。
5. 撤销 D 的席位 → D 刷新后立即失去企业空间；其上传内容仍在「财务」，C 可继续管理。
6. C 没有席位时 → 同样看不到企业空间（Q118）。
7. 企业授权置为过期 → C/D 可浏览/下载/删除，上传、建夹、改名、改可见名单均被拒（只读）。
8. 把 D 移出企业再重新加入并分配席位 → D 看不到「财务」：退出时 ACL 行已清理，
   必须由管理员重新授权（不会继承旧权限）；D 之前上传的内容仍在，由 C 管理（Q117）。

### 4.3 文件夹（SC-151 / SC-158 / SC-159）

1. 「财务」里有文件时删除 → 拒绝，提示先清空或移动。
2. 清空后再删除（二次确认）→ 成功；ACL 行同步删除。
3. 任何角色在根目录上传 → 拒绝，提示先选文件夹。
4. 把 D 从「财务」名单移除 → D 刷新后看不到该文件夹与内容。

### 4.4 平台共享（SC-152）

1. 管理端「平台共享内容」上传一个模板、一个图片、一个 csv → 均成功。
2. 会员 A 登录 → 平台页签可按类型/分类/关键词检索并下载；无上传/编辑/下架入口。
3. A 直接调用管理端接口 → 403。
4. 管理端下架图片 → A 刷新后不可见；已下载副本不受影响。
5. 检查 A/B/C 的容量：平台内容体积 **不计入** 任何空间。

### 4.5 兼容与关闭投稿

1. 旧版桌面端（或 curl）调用 `POST /member/cloud-template/submit-share` →
   `MEMBER_SHARED_TEMPLATE_SUBMIT_CLOSED`。
2. 旧版桌面端调用 `/member/cloud-template/page|get` → 正常返回个人模板。
3. 旧版桌面端调用 `/member/shared-template/page|get` → 正常返回平台内容（含历史已通过条目）。

## 5. 管理端

```bash
cd yudao-cloud/yudao-ui/yudao-ui-admin-vue3
pnpm install --registry=npmmirror   # 首次
pnpm vitest run src/views/member/platformContent
```

手工：

1. 登录管理端 → 会员管理 → 「平台共享内容」可见。
2. 上传 / 编辑 / 上下架 / 删除四种操作按权限点生效。
3. 旧「共享模板审核」菜单不存在；直接访问旧路由不出现页面。

## 6. 桌面端（maxlabel 仓库）

```bash
cd /d/workspace/maxlabel
npm run typecheck
npm run test        # 纯函数与组件单测
# e2e 攒批再跑；本次至少跑一次共享空间主链路
```

手工：

1. 模板库 → 三页签：个人 / 企业 / 平台。
2. 个人：保存当前模板、上传文件、下载/打开、删除（二次确认）、容量条。
3. 企业：管理员建夹/改名单/删空夹；成员按夹上传、删自己的；无席位时没有企业页签。
4. 平台：检索、下载、无上传入口。
5. 原「发布到共享库」「组模板库」入口消失。

## 7. 并发与容量（SC-150 / FR-423）

- 用两个并发请求（脚本或两个客户端）同时上传接近剩余容量的文件：
  必须一个成功、一个被拒，最终 `SUM(size_bytes)` ≤ 上限。
- 删除与上传并发：删除先提交时，上传应按释放后的容量判断。

## 8. 显式不验证

| 项 | 理由 |
|---|---|
| 对象存储中孤立文件的自动清理 | `FileApi` 无稳定删除契约；本期只保证数据库配额正确 |
| 文件夹多级嵌套 / 拖动移动 | 规格未要求，单层实现 |
| 门面站 / 小程序展示共享空间 | 规格 Assumptions 明确本期不做 |
| 平台内容的匿名下载 | 规格要求登录 |
| 历史待审核/已驳回共享模板的展示 | Q115 关闭投稿，旧表只留对账 |
| 大规模压测（>200 并发） | 一期企业规模为几十到几百成员，不做容量目标承诺 |

## 9. 上线顺序与回滚

**顺序**（不可颠倒）：

1. `033-shared-content-space.sql`（5 表 + 迁移 + 菜单）、`034-platform-content-menu-category.sql`
   （菜单归入「软件管理」+ 分类表）；
2. 后端；
3. 管理端（新平台内容页、删旧审核菜单）；
4. 桌面端（三页签、移除投稿）。

**判据**：

- SQL 后旧表行数不变；迁移表行数 = 预期；
- 后端启动成功（约 70 秒），旧健康检查通过；
- 管理端能打开新页；桌面端能登录、拉个人模板、拉平台内容。

**回滚**：

1. 回退桌面端 → 管理端 → 后端（代码回退；旧表未被修改，旧逻辑可直接读）；
2. 新表可保留（不参与旧逻辑）；如需彻底清理，按依赖顺序：
   `maxlabel_share_folder_member` → `maxlabel_share_folder` →
   `maxlabel_share_content` → `maxlabel_share_space` → `maxlabel_platform_content`；
3. **不要删除** `maxlabel_label_template` / `maxlabel_shared_template`。

## 10. 已知风险

- **存量超限**：老用户模板可能超过新上限（Q121），上线后会立即变成只读；客服话术需提前准备。
- **管理员无席位**：企业 OWNER/ADMIN 若未分配席位，将无法进入企业空间；012 的企业中心需提示先分配席位。
- **对象存储容量**：孤立文件不自动清理，长期需要运维策略（R111）。
- **旧桌面端窗口期**：投稿入口会报「已关闭」；其余旧端点保持可用（R107）。
- **预签名兜底**：若文件存储后端不支持预签名（如本地存储），`downloadUrl` 会退回静态地址，
  个人/企业文件的隔离依赖接口鉴权与不可猜测路径；需要更强隔离时再切私有存储。
