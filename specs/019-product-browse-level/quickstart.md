# Quickstart：商品浏览等级端到端验证

**Feature**: `019-product-browse-level` | **Date**: 2026-11-16

目标：用可重复的步骤验证「公开 / 全部登录会员 / 指定等级会员」三档在商品列表、搜索、详情、
收藏、浏览历史、购物车、结算与下单中的表现。

## 1. 前置条件

- MySQL 已按 `yudao-cloud/sql/mysql/041-product-browse-level.sql` 升级 `product_spu`；
  漏跑会报 `Unknown column 'browse_level_type'`。
- 后端已用最新代码启动（本地单体聚合，`java -jar yudao-server/target/yudao-server.jar`）。
  ⚠️ 不要把 `--spring.profiles.active=local` 加到生产 jar 上（见项目记忆）。
- 管理端已启动并能登录。
- C 端 storefront 已启动；确认请求头带 `tenant-id`，登录后带 `Authorization`。

## 2. 自动化测试

### 服务端

```bash
cd yudao-cloud
# 判定矩阵、Service 身份解析、SQL 分页、详情/评价、收藏/历史
mvn -pl yudao-module-mall/yudao-module-product-server -am test \
  -Dtest='*BrowseLevel*Test' -Dsurefire.failIfNoSpecifiedTests=false

# 交易链路（加购 / 价格 / 下单）
mvn -pl yudao-module-mall/yudao-module-trade-server -am test \
  -Dtest='*BrowseLevel*Test' -Dsurefire.failIfNoSpecifiedTests=false

# 营销活动过滤
mvn -pl yudao-module-mall/yudao-module-promotion-server -am test \
  -Dtest='*BrowseLevel*Test' -Dsurefire.failIfNoSpecifiedTests=false
```

⚠️ 项目既有坑：不给 `-am` 会读到本地仓库里过期的兄弟模块 jar；给了 `-am` 又可能被 IDE 写进
`target/classes` 的脏 class 挡住。测试成片报 `NoClassDefFoundError` / `VerifyError` 时先清
`target/classes`，不要误判成新代码把项目改崩了。

### 管理端

```bash
cd yudao-cloud/yudao-ui/yudao-ui-admin-vue3
npm run test -- OtherForm.spec.ts
```

## 3. 准备数据

在管理端：

1. 会员 → 会员等级：创建两个启用等级，记为 **L1**（例如「银卡」）与 **L2**（例如「金卡」），
   记下它们的编号 `LEVEL_L1_ID` / `LEVEL_L2_ID`。
2. 准备三个会员账号：
   - `M0`：没有等级（`levelId` 为 0）；
   - `M1`：等级设为 L1；
   - `M2`：等级设为 L2。
3. 商品管理创建三个上架商品，其它设置分别为：
   - 商品 A：浏览等级 = **公开**；
   - 商品 B：浏览等级 = **全部登录会员**；
   - 商品 C：浏览等级 = **指定等级会员**，只勾选 **L2**。
4. 三个商品都使用同一个分类，名称都包含同一个关键字（例如「浏览等级测试」），便于搜索验证。

## 4. 手工验证矩阵

### 4.1 访客（未登录）

| 操作 | 期望 |
|---|---|
| 首页 / 分类 / 搜索「浏览等级测试」 | 只出现商品 A |
| 直接访问商品 B / C 的详情 URL | 返回「商品 SPU 不存在」，页面不显示名称 / 价格 / 图片 |
| 通过接口 `GET /app-api/product/spu/page?keyword=浏览等级测试` | `list` 只有 A，`total=1` |

### 4.2 已登录、无等级会员 `M0`

| 操作 | 期望 |
|---|---|
| 列表 / 搜索 | A 与 B 出现，C 不出现 |
| A / B 详情 | 正常打开 |
| C 详情 | 「商品 SPU 不存在」 |
| 收藏 C | 被拒绝，提示商品不存在 |

### 4.3 等级 L1 会员 `M1`

| 操作 | 期望 |
|---|---|
| 列表 / 搜索 | A 与 B 出现，C 不出现 |
| C 详情 | 「商品 SPU 不存在」 |
| 购物车直接加购 C（或调用 `POST /app-api/trade/cart/add`） | 拒绝，提示「商品不存在或不可购买」 |

### 4.4 等级 L2 会员 `M2`

| 操作 | 期望 |
|---|---|
| 列表 / 搜索 | A、B、C 都出现，`total=3` |
| C 详情 | 正常打开 |
| 加购 C → 进入结算 → 提交订单 | 全链路成功 |

### 4.5 等级变化后的即时生效（SC-200）

1. 用 `M1` 确认 C 不可见。
2. 在管理端把 `M1` 的等级改成 L2。
3. `M1` 不重新登录，刷新商城页面：
   - C 出现在列表；
   - C 详情可打开。

### 4.6 购物车 / 收藏 / 浏览历史（Q151）

1. 用 `M2` 把 C 加入购物车，并收藏 C、浏览 C。
2. 在管理端把 `M2` 降级为 L1。
3. `M2` 刷新：
   - 购物车列表里看不到 C；
   - 收藏列表里看不到 C；
   - 浏览历史里看不到 C；
   - 购物车若仍勾选了 C，提交结算被拒绝并提示移除。
4. 再把 `M2` 调回 L2 并刷新：三处记录重新出现，无需重新加购 / 收藏。

### 4.7 管理端校验

| 操作 | 期望 |
|---|---|
| 新建商品默认浏览等级 | 「公开」 |
| 选「指定等级会员」但不勾选等级，点保存 | 保存被拒绝，提示至少选择一个等级 |
| 勾选 L1、L2 保存后重新打开 | 两个等级保持勾选 |
| 从「指定等级会员」改回「公开」并保存，再重新打开 | 显示「公开」；此前的等级配置不再生效 |
| 修改商品其它字段后保存 | 浏览等级配置保持不变 |
| 保存浏览等级后检查商品状态 / 价格 / 库存 | 与保存前一致（FR-536） |

### 4.8 历史订单不受影响（FR-531）

1. 用 `M2` 购买商品 C，确认订单创建成功。
2. 在管理端把 `M2` 降级为 L1。
3. `M2` 打开订单详情、申请售后：原订单项仍完整可见，售后流程可正常进入。
4. 再次尝试购买 C：加购 / 立即购买被拒绝。

## 5. 与既有可见性的叠加

1. 把商品 C 同时勾选「企业专享」。
2. 用非企业管理员但等级为 L2 的会员访问公开商城：C **不出现**（企业专享条件先否决）。
3. 用企业管理员但等级为 L1 的账号进入企业中心：C **不出现**（浏览等级条件否决）。
4. 用企业管理员且等级为 L2 的账号进入企业中心：C 可见。

## 6. 存量商品与回滚

- 执行 `041` 后执行：

  ```sql
  SELECT COUNT(*) FROM product_spu WHERE browse_level_type <> 0;
  ```

  升级后立即查询应为 **0**（SC-201）。
- 回滚：把 `browse_level_type` 全部改回 0 即可让所有商品恢复公开；代码回滚不影响该列。

## 7. 明确不验证

- 高并发下的性能与数据库压力（本次只要求不新增 N+1，未设吞吐目标）。
- uni-app 小程序 / App 的原生页面样式（服务端过滤后行为一致，但本次不逐端截图验收）。
- 购物车角标数量（`GET /trade/cart/get-count`）与列表条数的一致性：沿用既有企业专享行为，
  角标可能包含已隐藏商品，不在本次范围。
- 营销活动页的视觉布局；只验证商品信息是否被过滤。
- 砍价记录页、优惠券适用商品展示：前者按用户历史参与记录保留，后者沿用优惠券模块既有逻辑，
  均不在本次浏览等级过滤范围。
- 印刷定制订单：使用独立的隐藏 SPU/SKU 与计价链路，不属于公开商品浏览，本次不改变其下单链路。
