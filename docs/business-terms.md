# 业务术语定义

测试题里 `business_term` 这一类的标准答案，都是按下面的定义写的。
程序读取的版本在 `src/main/resources/glossary.json`（每个术语带关键词和相关的表），提问里出现关键词时，把定义一起交给模型。两边要保持一致：改定义时两处都改。

对照组（baseline 配置）里模型看不到这些定义，这类题答错是正常的。这正是要对比的地方：**加词典前 vs 加词典后**。

| 术语 | 定义 | 对应题目 |
|---|---|---|
| 客户数 | 按 `customers.customer_unique_id` 去重计数。`customer_id` 是每个订单一个，同一个人多次下单会有多个，不能用来数人 | q010 |
| 复购客户 | 下过 2 个及以上订单的客户（按 `customer_unique_id` 统计） | q011 |
| GMV | 订单商品价格 `order_items.price` 之和，不含运费，排除状态为 `canceled`、`unavailable` 的订单；按下单时间归属年份 | q012 |
| 客单价 | 总支付金额 ÷ 订单数，即 `SUM(payment_value) / COUNT(DISTINCT order_id)`，数据来自 `order_payments` | q026 |
| 配送天数 | 签收日期减下单日期（`DATEDIFF(order_delivered_customer_date, order_purchase_timestamp)`），只统计已送达且有签收时间的订单 | q027 |
| 延迟送达 | 签收时间晚于预计送达时间：`order_delivered_customer_date > order_estimated_delivery_date` | q028 |
| 差评 / 差评率 | 评分 1–2 分算差评；差评率 = 差评条数 ÷ 评价总条数 | q029 |
| 活跃卖家 | 在统计期内（按订单下单时间）卖出过至少一件商品的卖家，按 `seller_id` 去重 | q030 |

考核集（`eval/test-cases.json`）里的业务术语题也严格按这些定义写，但换了问法和场景。
做词典时只能依据这份文档里的定义，不能参考考核集的题目。

## 加新术语时

1. 先在这里写清楚定义，确认没有歧义。
2. 再写对应的测试题，标准答案严格按定义写。
3. 同步加进 `glossary.json`：关键词要写用户自然会说的叫法，不要照着某道测试题的原话写。
