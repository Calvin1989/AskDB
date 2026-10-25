-- Olist 巴西电商数据集的建表语句
-- 字段顺序和 CSV 列顺序一致（导入时按位置对应）
-- 表注释和字段注释会原样交给模型，写清楚含义和关联关系能明显提高准确率

DROP TABLE IF EXISTS order_reviews;
DROP TABLE IF EXISTS order_payments;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS category_translation;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS sellers;
DROP TABLE IF EXISTS customers;

CREATE TABLE customers (
    customer_id              VARCHAR(32) PRIMARY KEY COMMENT '每个订单对应一个 customer_id，同一个人多次下单会有多个 customer_id',
    customer_unique_id       VARCHAR(32) NOT NULL    COMMENT '真实的唯一客户ID，统计客户人数要用这个字段',
    customer_zip_code_prefix VARCHAR(10)             COMMENT '邮编前5位',
    customer_city            VARCHAR(64)             COMMENT '城市（葡萄牙语小写）',
    customer_state           CHAR(2)                 COMMENT '州代码，如 SP、RJ',
    INDEX idx_customers_unique (customer_unique_id)
) COMMENT '客户表' CHARSET = utf8mb4;

CREATE TABLE sellers (
    seller_id              VARCHAR(32) PRIMARY KEY COMMENT '卖家ID',
    seller_zip_code_prefix VARCHAR(10)             COMMENT '邮编前5位',
    seller_city            VARCHAR(64)             COMMENT '城市（葡萄牙语小写）',
    seller_state           CHAR(2)                 COMMENT '州代码，如 SP、RJ'
) COMMENT '卖家表' CHARSET = utf8mb4;

CREATE TABLE products (
    product_id                 VARCHAR(32) PRIMARY KEY COMMENT '商品ID',
    product_category_name      VARCHAR(64)             COMMENT '商品类别（葡萄牙语），英文名见 category_translation',
    product_name_length        INT                     COMMENT '商品名称字数',
    product_description_length INT                     COMMENT '商品描述字数',
    product_photos_qty         INT                     COMMENT '商品图片数量',
    product_weight_g           INT                     COMMENT '重量（克）',
    product_length_cm          INT                     COMMENT '长（厘米）',
    product_height_cm          INT                     COMMENT '高（厘米）',
    product_width_cm           INT                     COMMENT '宽（厘米）'
) COMMENT '商品表' CHARSET = utf8mb4;

CREATE TABLE category_translation (
    product_category_name         VARCHAR(64) PRIMARY KEY COMMENT '商品类别（葡萄牙语），关联 products.product_category_name',
    product_category_name_english VARCHAR(64)             COMMENT '商品类别（英文）'
) COMMENT '商品类别中英文对照表' CHARSET = utf8mb4;

CREATE TABLE orders (
    order_id                      VARCHAR(32) PRIMARY KEY COMMENT '订单ID',
    customer_id                   VARCHAR(32) NOT NULL    COMMENT '下单客户，关联 customers.customer_id',
    order_status                  VARCHAR(16)             COMMENT '订单状态：delivered 已送达、shipped 已发货、canceled 已取消、unavailable 无货、invoiced、processing、created、approved',
    order_purchase_timestamp      DATETIME                COMMENT '下单时间',
    order_approved_at             DATETIME                COMMENT '付款审核通过时间',
    order_delivered_carrier_date  DATETIME                COMMENT '交给物流的时间',
    order_delivered_customer_date DATETIME                COMMENT '客户签收时间',
    order_estimated_delivery_date DATETIME                COMMENT '预计送达时间',
    INDEX idx_orders_customer (customer_id),
    INDEX idx_orders_purchase (order_purchase_timestamp)
) COMMENT '订单表' CHARSET = utf8mb4;

CREATE TABLE order_items (
    order_id            VARCHAR(32)   NOT NULL COMMENT '关联 orders.order_id',
    order_item_id       INT           NOT NULL COMMENT '订单内商品序号，从1开始；一个订单买了3件就有1、2、3三行',
    product_id          VARCHAR(32)   NOT NULL COMMENT '关联 products.product_id',
    seller_id           VARCHAR(32)   NOT NULL COMMENT '关联 sellers.seller_id',
    shipping_limit_date DATETIME               COMMENT '卖家最晚发货时间',
    price               DECIMAL(10, 2)         COMMENT '这件商品的售价（巴西雷亚尔，不含运费），销售额、平均售价按它算',
    freight_value       DECIMAL(10, 2)         COMMENT '这件商品分摊的运费',
    PRIMARY KEY (order_id, order_item_id),
    INDEX idx_items_product (product_id),
    INDEX idx_items_seller (seller_id)
) COMMENT '订单商品明细表（销售明细），每行是卖出的一件商品；统计卖出件数、销量、销售额都用这张表' CHARSET = utf8mb4;

CREATE TABLE order_payments (
    order_id             VARCHAR(32)   NOT NULL COMMENT '关联 orders.order_id',
    payment_sequential   INT           NOT NULL COMMENT '同一订单的第几笔支付（一个订单可以分多笔、多种方式支付）',
    payment_type         VARCHAR(20)            COMMENT '支付方式：credit_card 信用卡、boleto 银行单据、voucher 代金券、debit_card 借记卡',
    payment_installments INT                    COMMENT '分期数',
    payment_value        DECIMAL(10, 2)         COMMENT '这笔支付的金额',
    PRIMARY KEY (order_id, payment_sequential)
) COMMENT '订单支付表' CHARSET = utf8mb4;

CREATE TABLE order_reviews (
    review_id               VARCHAR(32) NOT NULL COMMENT '评价ID',
    order_id                VARCHAR(32) NOT NULL COMMENT '关联 orders.order_id',
    review_score            INT                  COMMENT '评分 1-5',
    review_comment_title    VARCHAR(255)         COMMENT '评价标题（葡萄牙语）',
    review_comment_message  TEXT                 COMMENT '评价内容（葡萄牙语）',
    review_creation_date    DATETIME             COMMENT '发出评价邀请的时间',
    review_answer_timestamp DATETIME             COMMENT '客户提交评价的时间',
    INDEX idx_reviews_order (order_id)
) COMMENT '订单评价表' CHARSET = utf8mb4;
