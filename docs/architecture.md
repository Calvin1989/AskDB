# AskDB 架构说明

## 一次提问经过哪几步

```
用户问题
   │
   ▼
AskController ──► AskService（总流程，只负责按顺序调用下面几步，每步通过 AskProgress 推送进度）
   │                 │
   │ /api/ask        ├─① AnswerCache        查缓存，命中直接返回
   │ /api/ask/stream ├─② GlossaryService    匹配业务术语（GMV、复购……），得到口径定义和相关的表
   │  （SSE 推进度）  ├─③ SchemaProvider     选表：按问题打分 + 术语要求的表 + 补齐关联的中间表
   │                 ├─④ ExampleStore       找已确认正确的相似问题当示例
   │                 ├─⑤ SqlGenerator       问题 + 表结构 + 术语 + 示例 → 模型 → SQL
   │                 ├─⑥ SqlExecutor        SqlGuard 安全检查，再执行
   │                 │      └─ 报错 → SqlGenerator.fix(报错信息) → 再执行，最多 N 次
   │                 └─⑦ 写缓存、QueryLogService 记调用日志
   │
   ▼
AskResult（问题、SQL、结果或错误、token、过程记录 trace、是否命中缓存、耗时）
```

请求进来先经过 RateLimitInterceptor（每个 IP 每分钟最多 N 次）。评测（EvalRunner）直接调用 AskService，测的是完整流程。

## 包结构

| 包 | 职责 | 主要的类 |
|---|---|---|
| `api` | HTTP 接口和统一的错误返回 | `AskController`、`FeedbackController`、`SettingsController`、`GlobalExceptionHandler` |
| `core` | 一次提问的总流程、进度通知、缓存、调用日志 | `AskService`、`AskProgress`、`AskResult`、`AnswerCache`、`QueryLogService` |
| `schema` | 读取表结构、按问题选表、表之间的关联 | `SchemaReader`、`TableSelector`、`TableRelations`、`RetrievalSchemaProvider`、`FullSchemaProvider` |
| `glossary` | 业务词典 | `GlossaryService`、`Term` |
| `examples` | 示例库（用户反馈） | `ExampleStore`、`ExampleMatcher` |
| `llm` | 模型设置、调模型生成和修正 SQL、拼提示词 | `SqlGenerator`、`PromptSections`、`LlmSettingsService`、`LlmClientFactory` |
| `sql` | SQL 安全检查和执行 | `SqlGuard`、`SqlExecutor`、`QueryResult` |
| `ratelimit` | 限流 | `RateLimiter`、`InMemoryRateLimiter`、`RedisRateLimiter`、`RateLimitInterceptor` |
| `text` | 切词和相似度（选表、找示例共用） | `TextTokens` |
| `eval` | 跑测试题、比较结果、出报告 | `EvalRunner`、`ResultComparator`、`EvalReport` |
| `dataimport` | 把 Olist 的 CSV 导入 MySQL、建只读账号（各只用一次） | `OlistImporter`、`ReaderAccountSetup` |
| `config` | 所有可调参数集中在一处 | `AskDbProperties` |

## 几条约定（防止代码越写越乱）

1. **AskService 只做编排，不写具体逻辑。** 每一步的逻辑放在各自的类里，AskService 读起来就是一份流程清单。
2. **配置只从 `AskDbProperties` 读。** 不在各个类里写死数字，也不到处用 `@Value`。
3. **提示词放在 `resources/prompts/` 下的文件里，不写在 Java 代码里。** 改提示词不用改代码，也方便对比不同版本。
4. **执行 SQL 只有 `SqlExecutor` 一个入口。** 问答和评测（包括执行标准答案 SQL）都走它，安全检查也只放在这一个地方。
5. **纯逻辑的类都有单元测试**（`SqlExtractor`、`SqlGuard`、`ResultComparator`、`SchemaFormatter`、`AnswerCache` 的键规则），不连数据库、不调模型也能测。
6. **只有确实会有第二种实现的地方才写接口。** 目前是 `SchemaProvider`（全部表 / 按问题选表，用来做对比实验）和 `RateLimiter`（内存 / Redis）。
7. **辅助功能出错不影响主流程。** 缓存读写失败、日志写入失败都只打警告，用户照样拿到结果。
8. **每项准确率改进都能单独关掉。** `application-baseline.yml` 全部关掉作为对照组，评测时一条命令就能对比。
9. **纯计算的逻辑和 Spring 分开写**（`TableSelector`、`TableRelations`、`ExampleMatcher`、`PromptSections`、`InMemoryRateLimiter`），不用启动 Spring 就能单元测试。

## SQL 安全：三层防护

| 层 | 做法 | 防的是什么 |
|---|---|---|
| 1. 代码检查 | `SqlGuard` 用 JSqlParser 解析，只放行单条 SELECT；表名前带库名的也拒绝 | 模型生成了 DELETE、DROP，在查询后面拼了第二条语句，或者去查 askdb_meta、information_schema 等其他库 |
| 2. 驱动限制 | MySQL 驱动默认 `allowMultiQueries=false` | 第一层漏掉时，多条语句也执行不了 |
| 3. 数据库权限 | 运行时用 `askdb_reader`：业务库只有 SELECT，日志表只有 INSERT，反馈表 SELECT + INSERT | 前两层都漏了，数据库也会拒绝修改 |

注意：JSqlParser 的 `parse()` 只解析第一条语句，`SELECT 1; DROP TABLE orders` 会被当成合法的 SELECT。必须用 `parseStatements()` 再检查条数，`SqlGuardTest` 里有这个用例。

另外，调用日志放在单独的库 `askdb_meta` 里。只读账号对它只有插入权限，所以模型生成的 SQL 看不到也改不了日志，`SchemaReader` 读表结构时也不会把日志表交给模型。

## 模型设置：可以在页面上修改

模型客户端不用 Spring AI 自动创建的那个（它在启动时就固定了），而是：

- `LlmSettingsService` 保存当前设置。启动时读 `askdb.llm.*`（来自配置文件、环境变量）；页面上保存过的话以 `askdb-llm.json` 为准
- `LlmClientFactory` 按设置创建客户端。手动创建时要把 `RestClient.Builder`（超时）、`RetryTemplate`（重试）、`ResponseErrorHandler`（429 可重试）接上，否则 application.yml 里的这些配置不生效
- `SqlGenerator` 每次调用都取当前客户端，所以改完立即生效，不用重启
- 接口 `GET/PUT /api/settings/llm`、`POST /api/settings/llm/test`，只返回打码后的 Key
- `askdb.llm.editable=false` 时禁止修改，部署到公网必须关掉
- 缓存键里包含接口地址和模型名，换了模型不会拿到旧模型的答案；评测报告记录模型名

数据库密码不能放在页面上配（服务启动就要连数据库），放在不提交的 `askdb-local.yml` 里，通过 `spring.config.import` 读取。

## 调模型：重试和超时

Spring AI 默认失败后最多重试 10 次，等待时间每次乘 5、最长 3 分钟，一个请求可能挂十几分钟。现在改为：

- 总共最多调用 3 次，等待 1 秒、2 秒
- 401 这类客户端错误（比如 Key 填错）不重试
- 429（请求太频繁）要重试。Spring AI 默认把所有 4xx 都当成不可重试，429 必须用 `on-http-codes` 单独列出
- 连接超时 5 秒，等待回复最多 60 秒

## 按问题选表

不调模型、不用向量库，用切词 + 打分，速度快，结果能解释：

1. 问题、每张表的"描述"（表名、表注释、字段名、字段注释）都切成词：中文按相邻两个字切，英文按单词切
2. 每个重合的词按 IDF 加权：只出现在一两张表里的词（"卖家"）比到处都有的词（"订单"）更有区分度；表名、表注释里的词权重是字段的 3 倍
3. 选取：问题直接提到了表名或表注释里有区分度的词，这张表一定选；其余的分数不低于最高分的 35% 才选，最多 4 张；再加上业务术语要求的表
4. 用字段注释里的"关联 表.字段"建一张关系图，最短路径补齐 JOIN 需要的中间表（问"客户支付了多少"会选出 customers、order_payments，再补上 orders）
5. 一张都没匹配上就给全部表，宁可多给也不能漏

只看相对分数会漏表：问"SP 州的卖家卖出了多少件商品"时，"卖出""商品"让订单明细表分数很高，卖家表被挤到线以下。所以加了第 3 步的"直接提到就一定选"。

表注释写得好不好直接决定选表效果，所以注释里要写上业务上的叫法（比如订单明细表写明"统计销量、销售额都用这张表"）。

离线检查（只看标准答案用到的表有没有被选中）：调参集 30/30，考核集 21/22，平均每题给模型约 3 张表（原来 8 张）。

## 自动修正

SQL 执行报错时，把"原来的 SQL + 数据库报错"交给模型改写（`prompts/sql-fix.st`），最多 `askdb.correction.max-attempts` 次。

SqlGuard 拦截分两种（`UnsafeSqlException.fixable`）：写法问题（解析不了、表名前带了库名）可以修正；想删改数据、一次执行多条语句的直接拒绝，不修正。token 用量把生成和每次修正加在一起。

## 示例库

用户在页面上点"对"，问题和 SQL 存进 `askdb_meta.feedback`；同一个问题以最后一次反馈为准。提问时用切词后的重合度（Jaccard）找最相似的几条，作为参考交给模型。

- 反馈的 SQL 也要过 SqlGuard，不能借反馈塞进修改数据的语句
- 完全相同的问题不当示例（否则评测等于直接给答案；线上相同问题本来也会走缓存）
- 部署到公网时，任何人都能提交反馈，示例可能被"投毒"。正式使用应该加登录，或者改成人工审核后才进示例库

## 进度推送（SSE）

`POST /api/ask/stream` 返回 `text/event-stream`。处理放在线程池里执行，请求线程立刻返回；每一步通过 `AskProgress` 推一个 `step` 事件，最后推 `result` 事件。浏览器自带的 EventSource 只支持 GET，前端用 fetch 读取流、按 SSE 格式自己解析。用户中途关掉页面时推送失败会被忽略，后台照常处理完并记日志。

## 限流

拦截器挡在提问、流式提问、反馈、测试连接这几个接口前面（会调模型或写数据），按 IP 计数，超出返回 429 和 `Retry-After`。

- 算法是滑动窗口：只看"最近 60 秒"的请求数，不会出现固定窗口在整点前后各刷一波的问题
- 内存版：每个 IP 一个时间队列，单机够用
- Redis 版：有序集合存请求时间，"删掉窗口外的 → 计数 → 没超就加一条"放在一个 Lua 脚本里，Redis 单线程执行脚本，并发时不会多放进来；Redis 出错时放行，限流挂了不能拖垮整个服务
- 部署在反向代理后面时要配 `server.forward-headers-strategy=native`，否则所有人共用代理的 IP

## 后面每周的功能插在哪里

| 周 | 功能 | 改动位置 | 状态 |
|---|---|---|---|
| 第 2 周 | 只放行 SELECT | `sql/SqlGuard`，`SqlExecutor` 执行前调用 | 已完成 |
| 第 2 周 | 只读账号 | `db/setup-reader.sql`，导入时用管理员账号 | 已完成 |
| 第 2 周 | 查询超时、限制行数 | `AskDbProperties.query` | 已完成 |
| 第 2 周 | 相同问题走 Redis 缓存 | `core/AnswerCache`，`AskService` 入口处调用 | 已完成 |
| 第 2 周 | 调用日志 + token 用量 | `core/QueryLogService`，`SqlGenerator` 返回 token 数 | 已完成 |
| 第 3 周 | 按问题选表 | `schema/RetrievalSchemaProvider`、`TableSelector`、`TableRelations` | 已完成 |
| 第 3 周 | 业务词典 | `glossary/GlossaryService`，数据在 `resources/glossary.json` | 已完成 |
| 第 4 周 | 报错后自动修正 SQL | `AskService` 里的修正循环，提示词 `prompts/sql-fix.st` | 已完成 |
| 第 4 周 | 示例库 + 反馈按钮 | `examples/ExampleStore`、`/api/feedback`、前端反馈按钮 | 已完成 |
| 提前 | 进度推送、限流 | `/api/ask/stream`（SSE）、`ratelimit` 包 | 已完成 |
| 第 4 周 | 问题有歧义时反问 | 改用结构化输出；`AskResult` 加一种"需要澄清"的状态 | |
| 提前 | 前端页面 | `askdb-web/`（Vue 3），开发时代理 `/api` 到后端，打包后放进后端静态目录 | 已完成 |
| 第 5 周 | 多轮追问 | 前端已按对话保存记录；请求里带上同一对话之前的问答，`SqlGenerator` 多一个历史参数 | |

## 评测怎么判对错

把模型生成的 SQL 和标准答案 SQL 都执行一遍，比较两份结果（`ResultComparator`）：

- 把标准答案的每一列配对到模型结果的某一列，配好后所有行都对得上才算一致
- 行的顺序、列名、列的顺序都不管
- 数字按两边小数位较少的一方比较（模型 `ROUND(x, 2)` 算对），但标准答案有小数时至少比 2 位（取整到个位不算对），最多比 4 位
- 模型多查了列单独记为 `EXTRA_COLUMNS`，报告同时给"严格"和"允许多列"两个准确率

这和 Spider 等 NL2SQL 评测集用的"执行准确率"思路一致。还不检查行的先后顺序：题目要"前 5 名"时只检查是不是这 5 个。

第一版是把每行的值排序后整体比较，有两个问题，已经改掉：
1. 模型 `ROUND(x, 2)` 得到 4.07、标准答案是 4.0709，会被判错，平均值、比例类的题会大量误判
2. 列对应关系丢了：标准答案 (A,1),(B,2)，模型 (A,2),(B,1)，单看每列都对，但其实是错的；以及行内值重复时的误判

评测时缓存强制关闭，也不写调用日志。

## 评测方法：防止"调到题目上"

- **调参集** `cases.json`（30 道）：改提示词、加功能时看它，可以逐题分析错因。
- **考核集** `test-cases.json`（22 道）：只在阶段节点跑，不看错因、不针对它调整。术语和调参集相同，但换了问法和场景。
- **重复运行**：`askdb.eval.repeat` 跑 N 次，汇总平均值和范围，并列出有时对有时错的题。对外报告的数字用考核集 3 次平均。每次评测的结果会存到 `eval-results/` 下，文件名带时间，包含准确率、平均耗时和平均输入 token，方便以后做"加功能前 vs 加功能后"的对比。
