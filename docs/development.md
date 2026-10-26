# 本地开发

不想装 Docker，或者要改代码、跑评测时，按这份文档在本机运行。只想先跑起来看看，用 README 里的 Docker 方式更快。

## 1. 准备

- JDK 17、MySQL 8（本地 3306 端口）、Node.js 18 或以上
- 一个大模型 API Key（DeepSeek 或通义千问）
- Olist 数据：从 [Kaggle](https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce) 下载，把 8 个 CSV 放在项目根目录的 `data/olist/` 下（该目录不提交到 Git）。放在别处的话，用环境变量 `OLIST_DATA_DIR` 指定
- Redis（可选，只有开启缓存时需要）

**IDEA 运行配置**：`.run/` 目录里已经配好了常用的运行配置，在 IDEA 右上角的下拉框里直接选：
`1 导入数据`、`2 建只读账号`、`AskDbApplication`（启动服务）、`单元测试`、各项评测。

## 2. 本地配置文件（放数据库密码）

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

## 3. 导入数据（只需一次）

在 IDEA 里运行 `1 导入数据`，或者用命令：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=import"
```

会自动建库 `askdb`、建表、导入 CSV，导完自动退出。

## 4. 创建只读账号（只需一次）

先确认 `askdb-local.yml` 里的 `DB_PASSWORD` 是你想给只读账号用的密码（不要和 root 一样），并且没有 `DB_USERNAME: root` 这一行。然后在 IDEA 里运行 `2 建只读账号`，或者用命令：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=setup-reader"
```

它会用 root 执行 `db/setup-reader.sql`：创建日志库 `askdb_meta`、调用日志表 `query_log`、反馈表 `feedback` 和只读账号 `askdb_reader`，再把账号密码设成 `DB_PASSWORD`。做完自动退出，可以重复执行。

也可以在 MySQL 客户端里用 root 手动执行 `db/setup-reader.sql`，再执行 `ALTER USER 'askdb_reader'@'%' IDENTIFIED BY '你的密码';`。

## 5. 启动服务，配置模型

直接运行 `AskDbApplication`（或 `mvn spring-boot:run`），打开网页（见下面第 6 节），点左下角的**模型设置**：

1. 选服务商（DeepSeek / 通义千问 / 其他 OpenAI 兼容服务），地址和模型名会自动填好
2. 粘贴 API Key
3. 点**测试连接**，成功后点**保存**

设置保存在后端的 `askdb-llm.json`（不提交到 Git），立即生效，重启后不用再填。页面上只显示 Key 的头尾几位，完整的 Key 不会返回给浏览器。

也可以不用页面：在 `askdb-local.yml` 里写 `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL` 作为初始值。页面上保存过之后，以页面保存的为准。

想在页面上用 Agent 模式（写 SQL 前模型可以自己查数据），在 `askdb-local.yml` 里加一行 `ASKDB_AGENT_ENABLED: true`，重启后端。

**部署到公网时**要设 `ASKDB_LLM_EDITABLE=false`，关掉页面修改，否则任何人都能改你的 Key。

启动后有三种方式提问：

1. **网页**：见下面第 6 节。
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
  "rewrittenQuestion": null,
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

## 6. 前端

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

## 7. 缓存和限流

**限流**默认开启：每个 IP 每分钟最多提问 10 次，超出返回 429，页面会提示还要等几秒。计数默认存在内存里；多台服务器部署时改成 `askdb.rate-limit.store: redis`，用 Redis 的有序集合 + Lua 脚本做滑动窗口，几台机器共享计数。评测不经过接口，不受限流影响。

**缓存**（可选）

相同问题第二次问时直接返回缓存，不再调模型。Windows 上用 Docker 启动 Redis 最方便：

```powershell
docker run -d --name askdb-redis -p 6379:6379 redis:7
$env:ASKDB_CACHE_ENABLED="true"
```

Redis 挂了也不影响查询，只是不走缓存。


## 8. 跑测试和评测

- 单元测试：IDEA 里运行 `单元测试`，或者 `mvn test`。本机开着 Docker 时，会顺带跑真实 MySQL 上的集成测试，没开就自动跳过
- 评测：见 [evaluation.md](evaluation.md)
