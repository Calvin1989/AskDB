/** 和后端 AskResult 对应的类型 */

export type AskStatus = 'SUCCESS' | 'GENERATION_FAILED' | 'REJECTED' | 'EXECUTION_FAILED'

export interface QueryResult {
  columns: string[]
  rows: unknown[][]
  truncated: boolean
}

export interface TokenUsage {
  promptTokens: number
  completionTokens: number
}

/** 这次提问的过程：用了哪些表、术语、示例，修正了几次 */
export interface Trace {
  tables: string[]
  totalTables: number
  terms: string[]
  examples: number
  corrections: number
}

export interface AskResult {
  question: string
  sql: string | null
  result: QueryResult | null
  status: AskStatus
  error: string | null
  usage: TokenUsage | null
  trace: Trace | null
  cached: boolean
  elapsedMs: number
}

/** 处理过程中后端推送的进度 */
export interface ProgressStep {
  stage: string
  message: string
}

export type Verdict = 'CORRECT' | 'WRONG'

/** 对话里的一问一答 */
export interface Turn {
  id: string
  question: string
  askedAt: number
  /** pending：等待后端返回；done：拿到了结果（结果本身可能是失败状态）；failed：请求没发出去或服务出错 */
  state: 'pending' | 'done' | 'failed'
  /** 等待结果时收到的进度 */
  steps?: ProgressStep[]
  answer?: AskResult
  requestError?: string
  /** 用户对结果的反馈 */
  feedback?: Verdict
  feedbackError?: string
}

export interface Conversation {
  id: string
  title: string
  turns: Turn[]
  updatedAt: number
}
