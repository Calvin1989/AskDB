/**
 * 常见报错对应的处理办法。后端把原始错误返回给前端，这里补一句"该检查什么"。
 * 匹配不到时返回 null，只显示原始错误。
 */
const HINTS: { pattern: RegExp; hint: string }[] = [
  { pattern: /Access denied/i, hint: '数据库账号或密码不对：还没建只读账号的话，先在 IDEA 里运行“2 建只读账号”再重启后端；建过的话，检查项目根目录 askdb-local.yml 里的 DB_PASSWORD。' },
  { pattern: /\b401\b|Unauthorized|invalid.{0,10}api.?key|Incorrect API key/i, hint: '模型 API Key 无效：点左下角的"模型设置"重新填写。' },
  { pattern: /Communications link failure|Connection refused/i, hint: '连不上 MySQL：确认 MySQL 已经启动。' },
  { pattern: /\b429\b|rate.?limit/i, hint: '请求太频繁，被模型服务限流了，稍等一会儿再试。' },
  { pattern: /Unknown database|doesn't exist/i, hint: '数据库或表不存在：确认已经按 README 导入过数据。' },
]

export function hintFor(error: string | null | undefined): string | null {
  if (!error) return null
  return HINTS.find((h) => h.pattern.test(error))?.hint ?? null
}
