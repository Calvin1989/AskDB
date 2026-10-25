/**
 * 把 SQL 切成小段用于着色。只区分关键字、字符串、数字、其他，够看清结构就行。
 * 切成片段后由模板逐段渲染（不拼 HTML），不存在注入问题。
 */
export type SqlToken = { type: 'keyword' | 'string' | 'number' | 'plain'; text: string }

const KEYWORDS = new Set([
  'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'NOT', 'IN', 'IS', 'NULL', 'AS', 'ON', 'JOIN', 'LEFT', 'RIGHT',
  'INNER', 'OUTER', 'GROUP', 'BY', 'ORDER', 'HAVING', 'LIMIT', 'DISTINCT', 'COUNT', 'SUM', 'AVG', 'MIN',
  'MAX', 'CASE', 'WHEN', 'THEN', 'ELSE', 'END', 'ASC', 'DESC', 'WITH', 'UNION', 'ALL', 'BETWEEN', 'LIKE',
  'DATE', 'YEAR', 'MONTH', 'DATEDIFF', 'DATE_FORMAT', 'ROUND', 'EXISTS', 'DELETE', 'UPDATE', 'INSERT',
  'DROP', 'INTO', 'SET', 'VALUES',
])

const TOKEN = /('(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*")|(\b\d+(?:\.\d+)?\b)|([A-Za-z_][A-Za-z0-9_]*)|([\s\S])/g

export function tokenizeSql(sql: string): SqlToken[] {
  const tokens: SqlToken[] = []
  for (const m of sql.matchAll(TOKEN)) {
    const [text, str, num, word] = m
    let type: SqlToken['type'] = 'plain'
    if (str) type = 'string'
    else if (num) type = 'number'
    else if (word && KEYWORDS.has(word.toUpperCase())) type = 'keyword'

    const last = tokens[tokens.length - 1]
    if (type === 'plain' && last?.type === 'plain') {
      last.text += text
    } else {
      tokens.push({ type, text })
    }
  }
  return tokens
}
