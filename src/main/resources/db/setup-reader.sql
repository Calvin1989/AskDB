-- 导入数据之后执行一次（docs/development.md 第 4 步）。推荐用 setup-reader 配置运行程序，
-- 它会用 root 执行这个脚本，再把账号密码改成 askdb-local.yml 里的 DB_PASSWORD。
-- 可以重复执行：已经存在的库、表、账号不会被覆盖。升级后有新表时再执行一次即可。
--
-- 不要把真实密码写进这个文件：它会提交到 Git。这里只用占位密码建账号。

-- 1. 调用日志单独放一个库 askdb_meta，和业务数据分开：
--    模型生成的 SQL 只能查 askdb 库，看不到也改不了日志
CREATE DATABASE IF NOT EXISTS askdb_meta DEFAULT CHARSET utf8mb4;

CREATE TABLE IF NOT EXISTS askdb_meta.query_log (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    question          VARCHAR(500)  NOT NULL COMMENT '用户的问题',
    generated_sql     TEXT                   COMMENT '模型生成的 SQL',
    status            VARCHAR(32)   NOT NULL COMMENT 'SUCCESS / GENERATION_FAILED / REJECTED / EXECUTION_FAILED',
    error             VARCHAR(1000)          COMMENT '失败原因',
    row_count         INT                    COMMENT '返回行数',
    prompt_tokens     INT                    COMMENT '输入 token 数',
    completion_tokens INT                    COMMENT '输出 token 数',
    cached            TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否命中缓存',
    elapsed_ms        BIGINT        NOT NULL COMMENT '总耗时（毫秒）',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_query_log_created (created_at),
    INDEX idx_query_log_status (status)
) COMMENT '每次提问的调用日志';

-- 用户反馈：标记"正确"的问题和 SQL 会作为示例，遇到相似问题时交给模型参考
CREATE TABLE IF NOT EXISTS askdb_meta.feedback (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    question      VARCHAR(500) NOT NULL COMMENT '用户的问题',
    generated_sql TEXT         NOT NULL COMMENT '被反馈的 SQL',
    verdict       VARCHAR(16)  NOT NULL COMMENT 'CORRECT = 结果正确，WRONG = 结果不对',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_feedback_created (created_at)
) COMMENT '用户对查询结果的反馈';

-- 2. 只读账号：业务库只能查，日志表只能插入，反馈表能查和插入
--    模型生成的 SQL 不能带库名（代码里会拦截），所以查不到 askdb_meta 里的内容
--    本地开发用 '%'；部署到服务器时改成应用所在的主机地址
CREATE USER IF NOT EXISTS 'askdb_reader'@'%' IDENTIFIED BY 'change_me';
GRANT SELECT ON askdb.* TO 'askdb_reader'@'%';
GRANT INSERT ON askdb_meta.query_log TO 'askdb_reader'@'%';
GRANT SELECT, INSERT ON askdb_meta.feedback TO 'askdb_reader'@'%';
