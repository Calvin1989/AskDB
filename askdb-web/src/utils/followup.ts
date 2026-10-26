import type { Turn } from '../types'

/** 后端最多参考前面 3 个问题，多发也没用 */
const MAX_HISTORY = 3

/** 实际拿去查询的问题：追问被改写过就用改写后的完整问题 */
export function effectiveQuestion(turn: Turn): string {
  return turn.answer?.rewrittenQuestion ?? turn.question
}

/**
 * 发给后端的上文：同一对话里已经有结果的问题，取最近几个。
 * 用改写后的完整问题，这样连续追问（"12 月呢？" → "其中 SP 州的呢？"）也能接得上。
 */
export function historyFor(turns: Turn[]): string[] {
  return turns
    .filter((t) => t.state === 'done' && t.answer)
    .slice(-MAX_HISTORY)
    .map(effectiveQuestion)
}
