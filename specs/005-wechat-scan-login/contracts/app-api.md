# 契约：C 端接口（`/app-api`）

> 路径前缀一律 `/app-api`（宪法：C 端 MUST NOT 走 `/admin-api`）。
> 除注明"既有"的以外，下面都是本特性**新增**的。

---

## 0. 复用的既有接口（不改，列出来是为了标明边界）

| 端点 | 作用 | 本特性怎么用 |
|---|---|---|
| `GET /member/auth/social-auth-redirect?type=31&redirectUri=` | 拿微信授权页地址 | **复用**。二维码的内容就是它返回的地址（拼上一次性 `state`） |
| `GET /system/area/tree` 等 | — | 与本特性无关 |
| `POST /infra/file/upload` | 上传文件 | **复用**，但只在**提交成功之后**调（它要求登录，见 FR-151） |
| `GET /member/user/get` | 会员资料 | **改动**：返回多一个 `company` |
| `PUT /member/user/update` | 改会员资料 | **改动**：接受 `company`（Q24：前台可看可改） |

---

## 1. `POST /member/auth/wechat/scan/create` —— 签发登录票据

**用途**：PC 点「微信登录」时调，拿到一张票据与二维码内容。

请求：无 body（可选 `terminal`）。

响应：

```json
{
  "ticket": "8f3a…（密码学随机，不可推导）",
  "qrContent": "https://open.weixin.qq.com/connect/oauth2/authorize?...&state=…",
  "expiresIn": 300
}
```

**约束**

- `ticket` MUST 不可猜测（FR-149）—— MUST NOT 用自增 / 时间戳 / 会员编号
- `state` 与 `ticket` **不是同一个值**（R19）：`state` 会出现在二维码与回跳 URL 里，可能被截图或进日志
- `expiresIn` 与 FR-152 的默认值一致（**5 分钟**）

---

## 2. `GET /member/auth/wechat/scan/query?ticket=…` —— PC 轮询

**用途**：PC 定时查这次扫码到哪一步了。

响应（按 `status` 取值不同）。**状态取值以 [data-model.md](../data-model.md) §4 为唯一口径（共 5 个）**；
**没有「已扫待确认」** —— 公众号网页授权不回调"已扫"事件，服务端观测不到那一刻：

```json
{ "status": "待扫" }                                  // 继续轮询
{ "status": "待补全", "wxNickname": "张三", "wxAvatar": "https://…" }   // PC 切到补全页
{ "status": "已完成", "accessToken": "…", "refreshToken": "…" }        // PC 登录成功，票据已被取走
{ "status": "已过期" }                                 // 停止轮询，提示重新扫码（FR-140）
{ "status": "已作废" }                                 // 客户自己关掉了补全页
```

**约束**

- **必须有停止条件**（FR-144）：`已完成` / `已过期` / `已作废` 都是终态，PC MUST NOT 继续轮询
- `已完成` 之后票据**立刻失效**（一次性，FR-149）—— 再查应返回 `已过期`，MUST NOT 再次吐出 token
- 轮询 MUST NOT 把请求叠起来（响应变慢时不要并发重发），照 `QrPayDialog` 的做法
- **`待补全` 之前 MUST NOT 返回任何 token**（FR-150）

---

## 3. `POST /member/auth/wechat/callback` —— 手机侧把授权结果交给服务端

**用途**：客户在手机微信里点完"允许"后，微信回跳到我们域名上的一个页面，
**那个页面**（跑在手机上）把 `code` + `state` 发到这里。

请求：

```json
{ "code": "…", "state": "…" }
```

响应：`{ "ok": true }`（不返回任何令牌 —— 令牌要 PC 那边轮询去取）

**约束**

- 服务端在这一步**把 code 换成 openid**，写进票据，状态置为 `待补全`（FR-132）
- **MUST NOT 在这一步建会员、建绑定或签发令牌**（FR-150）—— 这是整条链路最容易写错的地方
- `state` 失效 / 不属于任何票据 → 明确拒绝，且**不修改任何数据**

---

## 4. `POST /member/auth/wechat/complete` —— 提交补全页

**用途**：客户填完手机号（+ 选填公司名称 / 头像）后提交。**这一步才建号或绑定。**

请求：

```json
{
  "ticket": "8f3a…",
  "mobile": "13800138000",
  "smsCode": "1234",
  "company": "某某公司",
  "avatarUrl": "https://…（提交成功后才上传，故通常为空或稍后另调 update）"
}
```

响应：`{ "ok": true }`（令牌不从这条路径返回，PC 继续轮询 `query` 取）

**服务端的三条分支**（每一条都对应一个验收标准）：

| 情况 | 行为 | 依据 |
|---|---|---|
| 手机号**不是**既有会员 | **此刻新建会员**（带 mobile / company），绑定 openid | FR-134 / FR-150 |
| 手机号**是**既有会员 | **复用那个会员**，绑定 openid，**MUST NOT 新建** | FR-136 / SC-050 |
| 该会员**已绑过另一个微信** | **拒绝**并告知，先绑的那个**一点不动** | FR-146 / SC-053 |

**约束**

- 短信验证码**必须先验通过**才写任何东西（FR-147）；不通过 → 会员 0 增量、绑定 0 增量、也无登录态（SC-054）
- 票据过期 / 已作废 → 明确提示"本次登录已过期，请重新扫码"（FR-152）
- 建号时 `nickname` / `avatar` **非空**，微信没给就补默认值

---

## 5. `POST /member/auth/wechat/abandon` —— 客户关闭补全页

**用途**：让这次扫码**彻底作废**（FR-148）。

响应：`{ "ok": true }`

**约束**

- MUST 作废票据：不建会员、不建绑定、不留登录态（SC-055 三个 0）
- **这不是"登出"** —— 从来就没有登录过（Q26）
- 幂等：重复调用不报错

---

## 6. 变更的既有接口

### `GET /member/user/get` / `PUT /member/user/update`

新增一个字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `company` | `string \| null` | 公司名称。选填，最长 64 字符（Q24） |

**约束**：这两个接口**本来就要求登录**；补全页不能用它们（那时还没登录）——
补全页的 `company` 走 §4 那条路径写进去。
