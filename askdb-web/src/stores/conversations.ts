import { computed, reactive, watch } from 'vue'
import { askQuestionStream, RequestError, sendFeedback } from '../api/ask'
import type { Conversation, Turn, Verdict } from '../types'

/**
 * 对话记录，保存在浏览器 localStorage 里，刷新页面后还在。
 * 目前每个问题都是独立查询；第 5 周做多轮追问时，会把同一对话里之前的问题一起发给后端。
 */

const STORAGE_KEY = 'askdb.conversations.v1'
/** 最多保留多少个对话，超出时删掉最旧的，避免 localStorage 写满（一般上限 5MB） */
const MAX_CONVERSATIONS = 50
const TITLE_LENGTH = 40

const state = reactive({
  conversations: load(),
  activeId: null as string | null,
})

watch(() => state.conversations, save, { deep: true })

export function useConversations() {
  const sorted = computed(() => [...state.conversations].sort((a, b) => b.updatedAt - a.updatedAt))
  const active = computed(() => state.conversations.find((c) => c.id === state.activeId) ?? null)
  const busy = computed(() => active.value?.turns.some((t) => t.state === 'pending') ?? false)

  /** 开始新对话：先不创建记录，等第一个问题发出时再建，避免留下空对话 */
  function startNew() {
    state.activeId = null
  }

  function select(id: string) {
    state.activeId = id
  }

  function remove(id: string) {
    state.conversations = state.conversations.filter((c) => c.id !== id)
    if (state.activeId === id) state.activeId = null
  }

  async function ask(question: string) {
    const conversation = active.value ?? createConversation(question)
    const turn: Turn = { id: newId(), question, askedAt: Date.now(), state: 'pending' }
    conversation.turns.push(turn)
    conversation.updatedAt = turn.askedAt

    // 通过 reactive 数组取回代理对象，后面的修改才会触发界面更新和保存
    const live = conversation.turns[conversation.turns.length - 1]
    live.steps = []
    try {
      live.answer = await askQuestionStream(question, (step) => live.steps!.push(step))
      live.state = 'done'
    } catch (e) {
      live.state = 'failed'
      live.requestError = e instanceof RequestError ? e.message : '请求失败，请稍后再试。'
    }
  }

  /** 标记结果对不对。标"对"的问题和 SQL 会进入示例库，以后相似的问题会参考 */
  async function giveFeedback(turn: Turn, verdict: Verdict) {
    if (!turn.answer?.sql) return
    turn.feedbackError = undefined
    try {
      await sendFeedback(turn.question, turn.answer.sql, verdict)
      turn.feedback = verdict
    } catch (e) {
      turn.feedbackError = e instanceof RequestError ? e.message : '提交失败，请稍后再试。'
    }
  }

  function createConversation(firstQuestion: string): Conversation {
    const title = firstQuestion.length > TITLE_LENGTH ? firstQuestion.slice(0, TITLE_LENGTH) + '…' : firstQuestion
    state.conversations.push({ id: newId(), title, turns: [], updatedAt: Date.now() })
    if (state.conversations.length > MAX_CONVERSATIONS) {
      const oldest = [...state.conversations].sort((a, b) => a.updatedAt - b.updatedAt)[0]
      remove(oldest.id)
    }
    const created = state.conversations[state.conversations.length - 1]
    state.activeId = created.id
    return created
  }

  return { conversations: sorted, active, busy, startNew, select, remove, ask, giveFeedback }
}

function load(): Conversation[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const list: Conversation[] = raw ? JSON.parse(raw) : []
    // 页面刷新时还没返回的问题，结果已经拿不到了
    for (const c of list) {
      for (const t of c.turns) {
        if (t.state === 'pending') {
          t.state = 'failed'
          t.requestError = '页面刷新时这个问题还没有返回结果，请重新提问。'
        }
      }
    }
    return list
  } catch {
    return []
  }
}

function save(list: Conversation[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list))
  } catch {
    // 存储空间满了或被浏览器禁用：不影响当前使用，只是刷新后记录会丢
  }
}

function newId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`
}
