# LabelShop 上游只读契约（018）

> 依据：`C:\Users\liyan\labelshop-export\labelshop_export.py` 与 `.bak` 已验证的协议。
> 本期只调用 **4 个只读过程**；任何新增 / 修改 / 删除 / 上传 / 审核类过程一律拒绝。

## 0. 连接与客户端标识

| 项 | 值 |
|---|---|
| Base URL | `https://o.codingv.com/v1`（可配置 `yudao.maxlabel.labelshop.base-url`） |
| TLS | 使用 JDK 默认证书链与主机名校验（2026-11-13 实测上游证书有效；不再关闭校验） |
| `mid` | 配置 `yudao.maxlabel.labelshop.mid`；缺省 `UUID.nameUUIDFromBytes(("labelshop:" + hostname).getBytes(UTF_8))` |
| `mname` | 服务器主机名 |
| UA | `LabelShop/{short} ({os}; M/{mid.toLowerCase()}) Version/1.1.0.{build} language/zh-CN` |
| 默认版本 | `short=6.39`、`long=6.39.2511.36`、`build=2511`、`os=Windows 10; x64`（均可配置） |
| 超时 | connect 30s / read 60s；单条请求不自动重试 |

**只读白名单**：

```text
CodingV.Template.GetCateTree
CodingV.Template.GetCateList
CodingV.Template.GetTemplate
CodingV.Account.AliyunSTSToken
```

`executetype` 固定 `0`（查询）。实现中 MUST 在调用前再次校验过程名白名单。

---

## 1. 登录

`POST {base}/LabelShop/Login/?d={urlencoded account}`

```jsonc
{
  "mid": "<mid>",
  "mname": "<hostname>",
  "pwd": "<MD5-UPPER(password)>",
  "sid": "1",
  "try": 2,
  "uname": "<account>"
}
```

- `Content-Type: application/json`；响应为 JSON。
- 成功：`datas.token`、`datas.id`（uid）、`datas.companys`（公司 / 组织列表）。
- 回退：若 `datas.token` 缺失，取响应 Cookie `cvstoken`；uid 取 `datas.id` 或 `cvsuid`。
- 失败：`token` 或 `uid` 缺失 → `MEMBER_LABELSHOP_LOGIN_FAILED`（文案不含密码）。
- `companys` 字段名大小写不敏感；ID 兼容 `id/uid/com_uid/companyid/...`，名称兼容
  `name/companyname/...`；无法解析的项跳过。
- 注意：旧版登录每次签发新 token，会让客户端之前的会话失效；导入成功后可能需要
  在客户端重新登录（已有行为，文档提示即可）。

---

## 2. Exec（只读）

`POST {base}/LabelShop/Exec/?p={proc}&i={uid}`

```jsonc
{
  "executetype": "0",
  "proc": "<READONLY_PROC>",
  "procparams": "<compact JSON, ensure_ascii=false>",
  "returntype": "2",
  "sign": "<MD5-UPPER(executetype=0&proc={proc}&procparams={pp}&time={tm}{token})>",
  "sql": "",
  "t": "<epoch seconds>",
  "time": "yyyy-MM-dd HH:mm:ss",
  "token": "<token>"
}
```

- Cookie：`cvstoken={token}; cvsuid={uid}`。
- `procparams` MUST 与签名使用的字符串**完全一致**（紧凑 JSON、无空格、中文不转义）。
- 响应 `code` 非 `0`/`null` → 上游错误；会话失效（厂商 code/message 表明未登录）按 Q141
  重登一次。
- 返回值：`datas` 为数组或对象，按具体过程解析。

---

## 3. 分类树 / 列表 / 模板

### 3.1 `GetCateTree`

```jsonc
{ "@MODE": 1, "@TYPE": 0, "@UID": "<uid>" }
```

返回 `datas`：`[{ "Cate_ID": 26, "Parent_ID": 0, "Name": "服装标签", ... }]`。

| 来源 | `@TYPE` | `@MODE` |
|---|---|---|
| 我的模板库（`Own_Type=12`） | **0** | **1** |
| 组模板库（`Own_Type=13`） | **12** | **4** |
| 公共模板库（`Own_Type=14`） | 14 | 1（**不读取**） |

> 2026-11-13 修正：原文档把「我的」写成 `TYPE=12/MODE=4`、把「组」写成 `TYPE=14/MODE=1`，
> 与完整版导出脚本相反，导致个人账号 `GetCateTree` 返回 500。

### 3.2 `GetCateList`

```jsonc
{ "@CATEID": 26, "@OWNTYPE": 13, "@UID": "<uid>" }
```

返回 `datas`：`[{ "Tmpl_Uuid": "...", "Tmpl_Name": "...", ... }]`。

- 我的模板库：按分类依次尝试 `@OWNTYPE=0 → 12 → 11`，取第一个非空；
  `0` 为 OSS 云模板（`cloud-json`，需 STS），`11/12` 为 `.lsdx`。
- **分类树保留 `Cate_ID=0`（CATE0）与 `Cate_ID=-1`（LabelShop5）**；CATE0 分类记为
  「我的模板」。分类自身的 `Own_Type=11` 时只尝试 `@OWNTYPE=11`（LabelShop5 老账号），
  `0`/缺省按 `0→12→11`，其他按 `12→11`。
- 组模板库：按分类依次尝试 `@OWNTYPE=12 → 11`，取第一个非空（新账号 12、老账号 11）。
- 企业/组织：`@CATEID=0, @OWNTYPE=1, @UID=<companyId>`。
- **不**调用 `@OWNTYPE=14`（公共模板库）或其他 `Own_Type`；不使用 `@CATEID=-1` 的隐式兜底
  （LabelShop5 通过分类树中的 `Cate_ID=-1` 节点显式处理）。

### 3.3 `GetTemplate`

```jsonc
{ "@TMPLID": "<uuid>", "@OWNTYPE": 12, "@UID": "<uid>", "@MODE": 0 }
```

返回 `datas.data[0].LABEL`（`.lsdx` XML 文本）与 `Thumb`（base64 缩略图，**忽略**）。
`LABEL` 为空 → 该条失败（`LABEL 为空`），不写空内容。

---

## 4. 企业 / 组织模板（STS + OSS）

### 4.1 `CodingV.Account.AliyunSTSToken`

```jsonc
{ "@NAME": "<mid>", "@DURA": 28800 }
```

返回 `datas[0]` 或 `datas`：

```jsonc
{
  "AccessKeyId": "...",
  "AccessKeySecret": "...",
  "SecurityToken": "...",
  "BucketEndpoint": "oss-cn-beijing.aliyuncs.com",
  "BucketTmpl": "cvstmpl",
  "BucketImage": "cvsimg"
}
```

### 4.2 OSS GET（V1 签名）

`GET https://{bucket}.{endpoint}/lp/{uuid}.json`（`.png` 缩略图不下载）

```text
canonical = "x-oss-security-token:" + SecurityToken + "\n/" + bucket + "/" + key
toSign    = "GET\n\n\n" + Date + "\n" + canonical
signature = Base64(HMAC-SHA1(AccessKeySecret, toSign))

Headers:
  Date: <RFC 1123 GMT>
  x-oss-security-token: <SecurityToken>
  Authorization: OSS <AccessKeyId>:<signature>
```

- `key = "lp/" + uuid + ".json"`（桶 `BucketTmpl`，默认 `cvstmpl`）。
- HTTP 非 200 → 该条失败；继续其余条目。
- 正文为 LabelShop 私有 JSON（`docid/lfrm/pfrm/dbs/...`），**原样归档**，不解析成 LabelDoc。

### 4.3 模板外链图片（公开 GET，2026-11-26）

模板正文里的 `<picture type="1" file="labelshop:\\codingv\<name>">` 指向 LabelShop 图片空间。
**不签名、不依赖 STS**，直接公开读取：

```text
GET https://cvsimg.oss-cn-beijing.aliyuncs.com/li/<name>
等价：http://i.codingv.com/li/<name>
```

- 基地址可配置：`yudao.maxlabel.labelshop.image-base-url`（默认上述 OSS）。
- 实测 `200 / image/png / 3461 字节 / 92×85`（与 LabelShop 本地缓存缩略图 MD5 一致）；
  当前 OSS 只有缩略图，原图地址不在本期。
- 只接受 PNG / JPEG / GIF / BMP / WebP（按魔数判断）；非图片、404、超过 16 MB → 记警告，模板照常导入。
- 子路径非法（`.` / `..`）→ 视为非 LabelShop 图片，不下载。

---

## 5. 会话与错误策略

| 场景 | 处理 |
|---|---|
| 登录失败（账号/密码/锁定） | 任务 `FAILED`，不产生任何明细 / 内容 |
| 枚举阶段会话失效 | 重登 **一次**，从当前分类继续；再失败 → 任务 `FAILED`，已枚举明细保留 |
| 导入阶段会话失效 | 重登 **一次**，从当前明细继续；再失败 → 任务 `FAILED`，已写入内容保留 |
| 单条正文为空 / 下载失败 / 写入失败 | 明细 `FAILED`，继续下一条；不触发重登 |
| 上游返回非 JSON / 超时 | 单条失败；连续失败不熔断，任务跑完为止 |
| STS 获取失败 | 企业/组织模板来源记失败（任务 `errorMessage`）；个人库、组库继续 |
| 某一家公司 `GetCateList` 失败 | 该公司记失败；其余公司与来源继续 |
| 个人库或组库分类树失败 | 该来源记失败；另一来源继续 |
| 全部来源都失败 | 任务 `FAILED`；不产生残缺内容 |
| 服务重启 | 启动 Runner 把 `PENDING/RUNNING` 置 `INTERRUPTED`，清 `running_org_id` |

**来源级失败**：不产生模板明细；任务 `errorMessage` 汇总原因，`libraries` 中该来源为 0。
部分来源失败时任务可为 `2 已完成` 但 `errorMessage` 非空；`failCount` 只统计模板级失败。

**禁止**：为每条失败反复重登（会反复踢掉其他客户端会话）。

---

## 6. 与 Python 脚本的差异

| 项 | Python | 服务端 |
|---|---|---|
| 机器码 | WMI UUID | 配置 / 主机名派生 UUID |
| 客户端版本 | 读本机 `LabelShop.exe` | 配置的 6.39 兜底 |
| 缩略图 | 下载 | 不下载（016 无字段） |
| 企业 JSON | 存文件 | 存 `maxlabel_share_content.content` 文本 |
| 清洗 | 不清洗 | 不清洗（Q138） |
| 目标 | 本地目录 | 016 个人 / 企业空间 |
| 错误 | 写 manifest | 写导入明细 + 任务计数 |
