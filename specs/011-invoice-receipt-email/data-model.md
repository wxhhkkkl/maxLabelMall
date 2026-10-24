# 数据模型（011）

**没有新表**；只加两列，票种列沿用 tinyint。

```sql
ALTER TABLE `trade_order_invoice`
  ADD COLUMN `email` varchar(128) NULL DEFAULT NULL COMMENT '接收发票/收据的邮箱（选填）' AFTER `bank_name`;

ALTER TABLE `trade_invoice_title`
  ADD COLUMN `email` varchar(128) NULL DEFAULT NULL COMMENT '接收发票/收据的邮箱（选填）' AFTER `bank_name`;
```

## 票种

| 值 | 名称 | 抬头必填 |
|---|---|---|
| 1 | 电子普通发票 | 企业：名称 + 税号；个人：名称 |
| 2 | 电子增值税专用发票 | 企业：名称 + 税号（个人 + 专票被拒） |
| **3** | **收据** | **企业/个人：只要名称**（Q61） |

- 邮箱：申请快照 + 抬头簿各一份；抬头簿选中时带出（Q64）。
- 迁移：`sql/mysql/020-invoice-receipt-email.sql`（幂等）。
