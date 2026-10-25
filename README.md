# AskDB（问数）

基于大模型的自然语言数据查询系统：用中文提问，自动生成 SQL、执行并返回结果。

数据使用 Kaggle 上的 [Olist 巴西电商数据集](https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce)（订单、商品、客户、卖家、支付、评价等 8 张表）。

## 技术栈

后端：Java 17 · Spring Boot 3.4 · Spring AI 1.0 · MySQL 8 · Redis · JSqlParser

前端：Vue 3 · TypeScript · Vite · ECharts

大模型通过 OpenAI 兼容接口调用，DeepSeek、通义千问都可以，在页面上的"模型设置"里切换。

## 当前进度

- [x] 第 1 周：问答主流程、Olist 数据导入、评测脚本（执行准确率）
- [x] 第 2 周：只放行 SELECT、只读账号、Redis 缓存、调用日志（含 token 用量）、模型调用重试和超时
- [x] 第 3 周：按问题选表、业务词典
- [x] 第 4 周：SQL 报错自动修正、示例库和反馈按钮（歧义反问待做）
- [x] 前端页面：对话界面、历史记录、自动出图、SQL 复制、导出 CSV、处理进度实时显示
- [x] 提问接口限流（内存 / Redis 滑动窗口）
- [ ] 第 5 周：多轮追问
- [ ] 第 6 周：Docker 部署

设计说明见 [docs/architecture.md](docs/architecture.md)。

## 评测结果

模型 deepseek-chat，temperature 0，2026-10-25 测试。按执行结果判分：生成的 SQL 和标准 SQL 执行出来的结果一致才算对。考核集每组跑 3 次取平均。

| 考核集（22 题 × 3 次） | 对照组 | 完整版 |
|---|---|---|
| 严格准确率 | 81.8% | **89.4%** |
| 允许多列的准确率 | 86.4% | **93.9%** |
| 平均输入 token | 1220 | **710（少 42%）** |

调参集（30 题）上，完整版严格准确率 93.3%，允许多列的准确率 100%。

- **对照组**：把全部 8 张表的结构交给模型，不用业务词典、示例库和自动修正。
- **完整版**：按问题选表、业务词典、示例库、SQL 报错自动修正全部打开。
- 两组用的是同一份提示词。

几点说明：

- 考核集只有 22 题，1 道题约等于 4.5 个百分点。完整版比对照组平均多答对约 1.7 题。
- 评测中自动修正一次都没有触发（生成的 SQL 都能直接执行），它的作用是兜底，准确率的提升里不包含它。
- "允许多列"多算对的，基本都是模型把排序用的统计值也一起返回了（比如问"哪个城市订单最多"，同时返回了订单数）。这对用户反而有用，所以没有强行去掉。
- 在调参集上发现模型会把一对多的表直接 JOIN，导致平均值被重复计算。提示词里加了一条通用规则后修好了，这条规则对两组同时生效。

## 快速开始

### 1. 准备

- JDK 17、MySQL 8（本地 3306 端口）
- 一个大模型 API Key（DeepSeek 或通义千问）
- Olist 数据：8 个 CSV 放在项目根目录的 `data/olist/` 下（该目录不提交到 Git）。放在别处的话，用环境变量 `OLIST_DATA_DIR` 指定
- Redis（可选，只有开启缓存时需要）

**IDEA 运行配置**：项目的 `.run/` 目录里已经配好了下面几步要用的运行配置，在 IDEA 右上角的下拉框里直接选：
`1 导入数据`、`2 建只读账号`、`AskDbApplication`（启动服务）、`单元测试`、`评测 调参集`、`评测 考核集 对照组 ×3`、`评测 考核集 完整版 ×3`。

### 2. 本地配置文件（放数据库密码）

把项目根目录的 `askdb-local.example.yml` 复制一份，改名为 `askdb-local.yml`，填上密码：

```yaml
DB_PASSWORD: 只读账号的密码
DB_ADMIN_PASSWORD: root 密码
```

`askdb-local.yml` 已加入 `.gitignore`，不会提交到 Git。启动时会自动读取，不用再在 IDEA 里配环境变量。环境变量仍然可以用，而且优先于这个文件。

两个数据库账号的用途：

| 账号 | 用途 | 对应配置 |
|---|---|---|
| root（管理员） | 只在导入数据、建只读账号时用 | `DB_ADMIN_USERNAME`（默认 root）、`DB_ADMIN_PASSWORD` |
| askdb_reader（只读） | 服务和评测运行时用；业务库只能查，日志表只能插入 | `DB_USERNAME`（默认 askdb_reader）、`DB_PASSWORD` |

这样即使模型生成了删改数据的 SQL，并且绕过了代码里的检查，数据库也会拒绝执行。

### 3. 导入数据（只需一次）

在 IDEA 里运行 `1 导入数据`，或者用命令：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=import"
```

会自动建库 `askdb`、建表、导入 CSV，导完自动退出。

### 4. 创建只读账号（只需一次）

先确认 `askdb-local.yml` 里的 `DB_PASSWORD` 是你想给只读账号用的密码（不要和 root 一样），并且没有 `DB_USERNAME: root` 这一行。然后在 IDEA 里运行 `2 建只读账号`，或者用命令：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=setup-reader"
```

它会用 root 执行 `db/setup-reader.sql`：创建日志库 `askdb_meta`、调用日志表 `query_log`、反馈表 `feedback` 和只读账号 `askdb_reader`，再把账号密码设成 `DB_PASSWORD`。做完自动退出，可以重复执行。

也可以在 MySQL 客户端里用 root 手动执行 `db/setup-reader.sql`，再执行 `ALTER USER 'askdb_reader'@'%' IDENTIFIED BY '你的密码';`。

### 5. 启动服务，配置模型

直接运行 `AskDbApplication`（或 `mvn spring-boot:run`），打开网页（见下面"前端"一节），点左下角的**模型设置**：

1. 选服务商（DeepSeek / 通义千问 / 其他 OpenAI 兼容服务），地址和模型名会自动填好
2. 粘贴 API Key
3. 点**测试连接**，成功后点**保存**

设置保存在后端的 `askdb-llm.json`（不提交到 Git），立即生效，重启后不用再填。页面上只显示 Key 的头尾几位，完整的 Key 不会返回给浏览器。

也可以不用页面：在 `askdb-local.yml` 里写 `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL` 作为初始值。页面上保存过之后，以页面保存的为准。

**部署到公网时**要设 `ASKDB_LLM_EDITABLE=false`，关掉页面修改，否则任何人都能改你的 Key。

启动后有三种方式提问：

1. **网页**：见下面"前端"一节。
2. **IDEA**：打开项目根目录的 `api.http`，点请求左边的绿色三角。
3. **PowerShell**：

```powershell
Invoke-RestMethod -Uri http://localhost:8088/api/ask -Method Post -ContentType "application/json; charset=utf-8" `
  -Body ([Text.Encoding]::UTF8.GetBytes('{"question": "每种订单状态各有多少单？"}')) | ConvertTo-Json -Depth 5
```

注意：Windows PowerShell 里的 `curl` 其实是 `Invoke-WebRequest` 的别名，网上常见的 `curl -X POST -H ... -d ...` 写法会报错；要用 curl 请写 `curl.exe`。

返回示例：

```json
{
  "question": "每种订单状态各有多少单？",
  "sql": "SELECT order_status, COUNT(*) AS cnt FROM orders GROUP BY order_status",
  "result": { "columns": ["order_status", "cnt"], "rows": [["delivered", 96478], ...], "truncated": false },
  "status": "SUCCESS",
  "error": null,
  "usage": { "promptTokens": 1520, "completionTokens": 24 },
  "cached": false,
  "elapsedMs": 1830
}
```

`status` 的取值：

| 值 | 含义 |
|---|---|
| SUCCESS | 成功 |
| GENERATION_FAILED | 调模型失败，或回复里取不出 SQL |
| REJECTED | 生成的不是单条 SELECT，没有执行 |
| EXECUTION_FAILED | SQL 执行报错 |

每次提问都会记到 `askdb_meta.query_log`，可以直接查：

```sql
SELECT status, COUNT(*), AVG(elapsed_ms), AVG(prompt_tokens) FROM askdb_meta.query_log GROUP BY status;
```

### 前端

前端在 `askdb-web/`，用 Vue 3 + TypeScript + Vite，图表用 ECharts。需要先安装 [Node.js](https://nodejs.org/)（18 或以上）。

```powershell
cd askdb-web
npm install          # 第一次需要
npm run dev          # 开发模式，打开 http://localhost:5173/
```

开发模式下，`/api` 开头的请求会自动转发给后端（8088 端口），所以后端也要同时启动。改前端代码后页面会自动刷新。

要让后端直接提供页面（不单独启动前端）：

```powershell
npm run build        # 打包到 src/main/resources/static/
```

然后重启后端，打开 http://localhost:8088/ 。打包结果不提交到 Git。

页面功能：

- 对话记录保存在浏览器里，刷新后还在，左侧可以切换和删除
- 结果自动选择展示方式：单个值大字显示；分类对比画柱状图（分类名长或多时横向）；年月、日期、月份画折线图；不适合画图的只显示表格
- 每个结果都能看 SQL、复制 SQL、导出 CSV（Excel 打开中文不乱码）
- 出错时显示原因，常见的配置问题（数据库密码、API Key、MySQL 没启动）会提示该检查什么

前端测试：`npm test`（展示方式选择、数字格式、CSV 转义、SQL 着色、报错提示）。

### 6. 缓存和限流

**限流**默认开启：每个 IP 每分钟最多提问 10 次，超出返回 429，页面会提示还要等几秒。计数默认存在内存里；多台服务器部署时改成 `askdb.rate-limit.store: redis`，用 Redis 的有序集合 + Lua 脚本做滑动窗口，几台机器共享计数。评测不经过接口，不受限流影响。

**缓存**（可选）

相同问题第二次问时直接返回缓存，不再调模型。Windows 上用 Docker 启动 Redis 最方便：

```powershell
docker run -d --name askdb-redis -p 6379:6379 redis:7
$env:ASKDB_CACHE_ENABLED="true"
```

Redis 挂了也不影响查询，只是不走缓存。

### 7. 准确率改进（都可以单独开关）

| 改进 | 做法 | 配置 |
|---|---|---|
| 按问题选表 | 问题和每张表的表名、注释、字段切词比对打分，选出相关的表，再按注释里的"关联"关系补齐 JOIN 需要的中间表 | `askdb.schema.strategy: retrieval`（`full` = 全部表） |
| 业务词典 | 问题里出现 GMV、复购、差评等词时，把口径定义交给模型，并带上相关的表 | `askdb.glossary.enabled`，词典在 `resources/glossary.json` |
| 报错自动修正 | SQL 执行报错（或表名带了库名这类写法问题）时，把报错交给模型改写，最多 2 次。删改数据这类被拦截的不修正 | `askdb.correction.max-attempts` |
| 示例库 | 页面上点"对"的问题和 SQL 存进示例库，遇到相似问题时作为参考交给模型 | `askdb.examples.enabled` |

`application-baseline.yml` 把这四项全部关掉，作为对照组。

**升级后要做的两件事：**

1. 用 root 再执行一次 `setup-reader.sql`（新增了反馈表 `askdb_meta.feedback`，脚本可以重复执行）。
2. 如果之前已经导入过数据，再导入一次：`olist-schema.sql` 里订单明细表的注释改了（补上"卖出、销量、售价"等说法，选表靠注释匹配）。

**选表的离线检查**（不调模型，只看标准答案用到的表有没有被选中）：调参集 30/30，平均每题给模型 2.8 张表；考核集 21/22，平均 3.1 张。原来每题都给全部 8 张。

### 8. 跑评测

IDEA 里直接运行 `评测 调参集`、`评测 考核集 对照组 ×3`、`评测 考核集 完整版 ×3`。用命令的话：

```powershell
# 全部改进打开（默认）
mvn spring-boot:run "-Dspring-boot.run.profiles=eval"

# 对照组：全部改进关掉
mvn spring-boot:run "-Dspring-boot.run.profiles=eval,baseline"
```

两次的准确率、平均输入 token 之差，就是这些改进带来的效果。想单独看某一项，可以在 `askdb-local.yml` 里只关那一项再跑。

**注意示例库对评测的影响**：评测时示例库也会生效。完全相同的问题不会当示例，但如果你在页面上把调参集里的题标过"对"，相似的题会互相参考，调参集分数会偏高。考核集的题不要在页面上问。

评测时缓存强制关闭，保证每道题都真正调用一次模型；评测也不写调用日志，免得和真实提问混在一起。

准确率给两个：**严格**（和标准答案完全一致）和**允许多列**（标准答案的列都对，但模型多查了列，比如顺手带上了 COUNT）。两者的差距就是"多查列"造成的错误。模型自己保留两位小数（`ROUND(x, 2)`）算对，取整到个位不算对。

#### 两套题：调参集和考核集

| 题集 | 文件 | 题数 | 用途 |
|---|---|---|---|
| 调参集 | `eval/cases.json` | 30 | 平时改提示词、加功能后看这个，可以逐题分析错因 |
| 考核集 | `eval/test-cases.json` | 22 | 只在阶段节点跑（基线、第 3 周末、第 4 周末），**不看错因、不针对它调整** |

分开的原因：如果在同一批题上反复调提示词再测，分数一定偏高，说明不了真实效果。考核集用到的业务术语和调参集一样，但换了问法和场景（比如"RJ 州的客户总数"、"复购客户占比"），用来检验改进是不是真的通用。

两套题都分 4 类：

| 分类 | 调参集 | 考核集 | 含义 |
|---|---|---|---|
| single_table | 7 | 4 | 单表查询 |
| aggregation | 8 | 5 | 统计、时间范围、比例 |
| join | 7 | 5 | 多表关联 |
| business_term | 8 | 8 | 含业务术语（客户数、复购、GMV、客单价等） |

所有标准答案都已在完整数据上执行过，结果非空且合理。

#### 选择题集和次数

```powershell
# 调参集跑 1 次（默认）
mvn spring-boot:run "-Dspring-boot.run.profiles=eval"

# 考核集跑 3 次取平均（阶段节点用）
$env:ASKDB_EVAL_SET="test-cases"
$env:ASKDB_EVAL_REPEAT="3"
mvn spring-boot:run "-Dspring-boot.run.profiles=eval"
```

评测用的是页面上保存的模型设置，报告里会记下模型名，方便比较不同模型。

即使 temperature 设为 0，模型输出也不完全固定，同一套题跑一次的分数可能有几个百分点的偶然波动。写进简历的数字用 3 次的平均值。跑多次时，最后会汇总平均值、最低到最高的范围，并列出"有时对有时错"的题。

`business_term` 这类题的答案依赖具体定义（见 [docs/business-terms.md](docs/business-terms.md)）。对照组（baseline）里模型看不到这些定义，只能按自己的理解写，口径和定义不一致时就会答错；打开业务词典后再对比。

每次评测输出总体和分类准确率、平均耗时、平均输入 token、自动修正后才答对的题数，明细保存在 `eval-results/eval-题集-时间-run次数.json`。

## 目录结构

```
src/main/java/com/askdb
├── api          HTTP 接口（提问、流式提问、反馈、模型设置）、统一错误返回
├── core         一次提问的总流程、进度通知、缓存、调用日志
├── schema       读取表结构、按问题选表、表之间的关联关系
├── glossary     业务词典
├── examples     示例库（用户反馈）
├── llm          模型设置、调模型生成和修正 SQL、拼提示词
├── sql          SQL 安全检查和执行
├── ratelimit    限流
├── text         切词和相似度（选表、找示例共用）
├── eval         评测
├── dataimport   导入 Olist 数据、建只读账号
└── config       配置项
src/main/resources
├── prompts      提示词（生成、修正）
├── db           建表语句、只读账号脚本
├── eval         测试题
└── glossary.json 业务词典
askdb-web/src
├── api          调用后端接口
├── stores       对话记录（保存在浏览器）
├── components   页面组件：侧栏、对话、回答、表格、图表、SQL
└── utils        展示方式选择、格式化、CSV、SQL 着色、报错提示
```
