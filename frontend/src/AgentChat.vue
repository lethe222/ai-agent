<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand"><span class="mark" aria-hidden="true">✦</span>灵感台</div>
      <button class="new-chat" type="button" @click="resetChat">＋ 新对话</button>
    </header>

    <main ref="viewport" class="viewport" aria-label="对话内容" @scroll="trackScroll">
      <section v-if="!messages.length" class="welcome">
        <span class="hero-mark" aria-hidden="true">✦</span>
        <p class="eyebrow">你的智能体工作台</p>
        <h1>今天想探索什么？</h1>
        <p class="intro">从一个问题开始，看看想法能走多远。</p>
        <div class="suggestions" aria-label="试试这些问题">
          <button v-for="item in suggestions" :key="item.text" @click="useSuggestion(item.text)">
            <span class="suggestion-icon" aria-hidden="true">{{ item.icon }}</span>
            <span><strong>{{ item.title }}</strong><small>{{ item.text }}</small></span>
            <span class="arrow" aria-hidden="true">↗</span>
          </button>
        </div>
      </section>

      <div v-else class="thread">
        <article v-for="message in messages" :key="message.id" class="message" :class="message.role">
          <div v-if="message.role === 'assistant'" class="assistant-name">
            <span class="mark mini" aria-hidden="true">✦</span>灵感智能体
          </div>
          <template v-if="message.text">
            <div v-if="message.role === 'assistant'" class="message-text markdown-body" v-html="renderMarkdown(message)"></div>
            <p v-else class="message-text">{{ message.text }}</p>
            <span v-if="loading && message.id === activeReplyId" class="cursor" aria-hidden="true"></span>
          </template>
          <Transition name="phase">
            <div v-if="message.status" class="status" :class="{ 'status-after-text': message.text }" role="status">
              <span class="status-spinner" aria-hidden="true"></span>
              <span>{{ message.status }}</span>
              <span class="status-time" aria-hidden="true">{{ message.elapsed }} 秒</span>
            </div>
          </Transition>
          <p v-if="message.note" class="note" :role="message.error ? 'alert' : 'status'">{{ message.note }}</p>
        </article>
      </div>
    </main>

    <footer class="composer-area">
      <form class="composer" @submit.prevent="sendStream">
        <textarea ref="inputField" v-model="inputMsg" rows="2" aria-label="输入你的问题"
          placeholder="给灵感台发个消息…" @keydown="handleEnter"></textarea>
        <div class="composer-bottom">
          <span class="mode"><i></i>{{ useMock ? '模拟演示' : 'SSE 实时连接' }}</span>
          <button v-if="loading" class="send stop" type="button" aria-label="停止生成" @click="stopStream">■</button>
          <button v-else class="send" type="submit" aria-label="发送消息" :disabled="!inputMsg.trim()">↑</button>
        </div>
      </form>
      <p class="hint">{{ useMock ? '演示回复由前端生成，不会调用模型或联网搜索。' : '回答由智能体生成，请核对重要信息。' }}</p>
    </footer>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/common'
import DOMPurify from 'dompurify'
import 'highlight.js/styles/github-dark.css'

const markdown = new MarkdownIt({
  // 模型输出中的原始 HTML 不直接执行，渲染结果再经过安全过滤。
  html: false,
  breaks: true,
  linkify: true,
  highlight(code, language) {
    if (language && hljs.getLanguage(language)) {
      return hljs.highlight(code, { language, ignoreIllegals: true }).value
    }
    return markdown.utils.escapeHtml(code)
  },
})
const renderedMessages = new Map()

function renderMarkdown(message) {
  const cached = renderedMessages.get(message.id)
  if (cached?.text === message.text) return cached.html
  const html = DOMPurify.sanitize(markdown.render(message.text))
  renderedMessages.set(message.id, { text: message.text, html })
  return html
}

const suggestions = [
  { icon: '⌕', title: '理解概念', text: '用生活例子解释什么是 SSE' },
  { icon: '✎', title: '整理思路', text: '帮我规划一个 Vue 学习路线' },
  { icon: '⚡', title: '快速上手', text: '举例说明智能体如何调用工具' },
]

const useMock = import.meta.env.VITE_USE_MOCK !== 'false'
const endpoint = import.meta.env.VITE_AGENT_STREAM_URL || '/api/agent/chat'
const inputMsg = ref('')
const messages = ref([])
const loading = ref(false)
const activeReplyId = ref(null)
const viewport = ref(null)
const inputField = ref(null)
let nextId = 0
let current = null
let follow = true

function useSuggestion(text) {
  inputMsg.value = text
  inputField.value?.focus()
}

function handleEnter(event) {
  // 中文输入法按回车选字时，不发送消息。
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  sendStream()
}

function trackScroll() {
  const el = viewport.value
  if (el) follow = el.scrollHeight - el.scrollTop - el.clientHeight < 64
}

function scrollToLatest(force = false) {
  if (force) follow = true
  nextTick(() => {
    const el = viewport.value
    if (el && follow) el.scrollTop = el.scrollHeight
  })
}

function pause(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal.aborted) return reject(signal.reason)
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', abort)
      resolve()
    }, ms)
    function abort() {
      clearTimeout(timer)
      reject(signal.reason)
    }
    signal.addEventListener('abort', abort, { once: true })
  })
}

async function mockStream(question, signal, onStatus, onText) {
  await pause(380, signal)
  onStatus('模拟调用工具 · 整理资料')
  await pause(620, signal)

  const answer = question.includes('SSE')
    ? '可以把 SSE 想成餐厅出菜。\n\n普通请求像是等整桌菜做好，一次端上来。SSE 则像服务员做好一道就送一道。浏览器保持一条连接，服务器可以把新内容不断送过来，所以回答能边生成边显示。\n\n接收时要特别注意：网络送来的每一包不一定刚好是一条完整消息。前端要先把半截存起来，等下一包到了再拼成完整事件。'
    : question.includes('学习路线')
      ? '可以用三个小项目练习 Vue。\n\n1. 做一个待办清单，理解 ref、v-model 和列表渲染。\n2. 做一个搜索页面，练习请求数据、处理失败以及显示加载状态。\n3. 做一个流式对话页，练习组件过渡、停止请求和分段显示。\n\n每个项目都先跑通最小功能，再加样式和交互。'
      : question.includes('调用工具')
        ? '以「帮我查明天的天气」为例。\n\n智能体先识别需要查询天气，再让服务端调用天气工具。工具把结果交回模型，模型把它整理成你能直接读懂的答复。\n\n界面可以依次显示「思考中 → 调用天气工具 → 正在回答」。这个演示只模拟这些界面状态。'
        : '我收到了你的问题：' + question + '\n\n当前处于演示模式。这段回复会分小段显示，你可以在生成时点停止，已经显示的文字会保留。\n\n连接支持 SSE 的后端后，这个界面就能展示智能体实际生成的内容。'

  const characters = [...answer]
  for (let i = 0; i < characters.length; i += 3) {
    await pause(28, signal)
    onText(characters.slice(i, i + 3).join(''))
  }
}

async function fetchStream(question, signal, onStatus, onText) {
  const url = new URL(endpoint, window.location.href)
  url.searchParams.set('message', question)
  const response = await fetch(url, { headers: { Accept: 'text/event-stream' }, signal })
  if (!response.ok) throw new Error('接口返回 HTTP ' + response.status)
  const contentType = response.headers.get('content-type') || ''
  if (!response.body || !/^text\/event-stream(?:\s*;|$)/i.test(contentType)) {
    throw new Error('接口没有返回 text/event-stream')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let eventType = 'message'
  let data = []
  let finished = false

  function readLine(line) {
    if (line === '') {
      if (data.length) {
        const payload = data.join('\n')
        if (eventType === 'done' || payload === '[DONE]') finished = true
        else if (eventType === 'status') onStatus(payload)
        else if (eventType === 'error') throw new Error(payload || '服务端报告生成失败')
        else if (eventType === 'message' || eventType === 'delta') onText(payload)
      }
      eventType = 'message'
      data = []
      return
    }
    const colon = line.indexOf(':')
    const field = colon < 0 ? line : line.slice(0, colon)
    let value = colon < 0 ? '' : line.slice(colon + 1)
    // SSE 只移除冒号后的一个可选空格，保留正文中的缩进。
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'data') data.push(value)
    if (field === 'event') eventType = value
  }

  try {
    while (!finished) {
      const { value, done } = await reader.read()
      // UTF-8 汉字、换行符和 SSE 事件都可能跨网络分包。
      buffer += done ? decoder.decode() : decoder.decode(value, { stream: true })
      let start = 0
      for (let i = 0; i < buffer.length && !finished; i++) {
        const char = buffer[i]
        if (char !== '\r' && char !== '\n') continue
        if (char === '\r' && i === buffer.length - 1 && !done) break
        readLine(buffer.slice(start, i))
        if (char === '\r' && buffer[i + 1] === '\n') i++
        start = i + 1
      }
      buffer = buffer.slice(start)
      if (done) break
    }
  } finally {
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}

async function sendStream() {
  const question = inputMsg.value.trim()
  if (!question || current) return

  inputMsg.value = ''
  messages.value.push({ id: ++nextId, role: 'user', text: question })
  messages.value.push({ id: ++nextId, role: 'assistant', text: '', status: '正在思考，请稍候', elapsed: 0, note: '', error: false })
  const reply = messages.value.at(-1)
  const run = { controller: new AbortController(), frame: null, pending: '', timer: null, startedAt: Date.now(), lastActivity: Date.now(), serverStatus: '' }
  current = run
  activeReplyId.value = reply.id
  loading.value = true
  scrollToLatest(true)

  // 只展示连接和等待状态，不猜测模型内部是否正在调用工具。
  run.timer = setInterval(() => {
    if (current !== run) return
    const now = Date.now()
    reply.elapsed = Math.floor((now - run.startedAt) / 1000)
    if (!run.serverStatus && now - run.lastActivity >= 4000) {
      reply.status = reply.text || run.pending ? '正在等待后续内容…' : '正在等待回复…'
    }
  }, 1000)

  function flush() {
    if (run.frame !== null) cancelAnimationFrame(run.frame)
    run.frame = null
    if (current !== run || !run.pending) return
    reply.text += run.pending
    run.pending = ''
    scrollToLatest()
  }

  function onText(text) {
    if (current !== run || run.controller.signal.aborted || !text) return
    run.lastActivity = Date.now()
    run.serverStatus = ''
    reply.status = '正在生成回答…'
    run.pending += text
    // 显式按浏览器帧合并文本，避免每个网络片段都触发一次渲染。
    if (run.frame === null) run.frame = requestAnimationFrame(flush)
  }

  function onStatus(status) {
    if (current === run && !run.controller.signal.aborted) {
      run.lastActivity = Date.now()
      run.serverStatus = status
      reply.status = status || (reply.text || run.pending ? '正在生成回答…' : '正在思考，请稍候')
      scrollToLatest()
    }
  }

  try {
    await (useMock ? mockStream : fetchStream)(question, run.controller.signal, onStatus, onText)
    if (!run.controller.signal.aborted && !reply.text && !run.pending) throw new Error('未收到回复正文')
  } catch (error) {
    if (current === run && !run.controller.signal.aborted) {
      reply.error = true
      reply.note = '生成失败：' + (error instanceof Error ? error.message : '请重试')
    }
  } finally {
    // 新对话会使旧请求失效，旧请求的收尾不能改动新对话。
    clearInterval(run.timer)
    if (current === run) {
      flush()
      if (run.controller.signal.aborted) reply.note = '已停止生成'
      reply.status = ''
      current = null
      activeReplyId.value = null
      loading.value = false
      scrollToLatest()
    }
  }
}

function stopStream() {
  current?.controller.abort()
}

function resetChat() {
  if (current) {
    if (current.frame !== null) cancelAnimationFrame(current.frame)
    clearInterval(current.timer)
    current.controller.abort()
    current = null
  }
  messages.value = []
  renderedMessages.clear()
  inputMsg.value = ''
  activeReplyId.value = null
  loading.value = false
  follow = true
  nextTick(() => inputField.value?.focus())
}

onBeforeUnmount(() => {
  if (current?.frame != null) cancelAnimationFrame(current.frame)
  if (current?.timer != null) clearInterval(current.timer)
  current?.controller.abort()
  current = null
})
</script>

<style>
:root {
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;
  color: #272437;
  background: #f7f7fa;
  font-synthesis: none;
  -webkit-font-smoothing: antialiased;
}
*, *::before, *::after { box-sizing: border-box; }
body { margin: 0; }
button, textarea { font: inherit; }
button { cursor: pointer; }
button:disabled { cursor: default; }
button:focus-visible, textarea:focus-visible { outline: 2px solid #7653e9; outline-offset: 3px; }
.app-shell { height: 100vh; height: 100dvh; display: flex; flex-direction: column; }
.topbar {
  display: flex; align-items: center; justify-content: space-between; flex-shrink: 0;
  padding: 14px clamp(16px, 5vw, 64px); border-bottom: 1px solid #e9e6f0;
  background: #fff;
}
.brand, .assistant-name { display: flex; align-items: center; gap: 10px; font-weight: 700; }
.mark, .hero-mark { display: grid; place-items: center; color: #fff; background: #6c4bd5; }
.mark { width: 32px; height: 32px; border-radius: 10px; }
.mark.mini { width: 26px; height: 26px; border-radius: 8px; }
.new-chat { border: 1px solid #e5e1ee; background: #fff; color: #4e4468; border-radius: 10px; padding: 8px 13px; font-size: 14px; }
.new-chat:hover { background: #f3eefc; }
.viewport { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain; }
.welcome {
  width: min(760px, 100%); margin: auto; padding: clamp(24px, 9vh, 105px) 20px 24px;
  text-align: center;
}
.hero-mark {
  width: 62px; height: 62px; margin: auto; border-radius: 20px; font-size: 32px;
  box-shadow: 0 10px 25px #7352d840; background: linear-gradient(145deg, #9269ee, #6946cf);
}
.eyebrow { margin: 25px 0 10px; color: #6345bc; font-weight: 700; font-size: 13px; letter-spacing: .12em; }
h1 { margin: 0; font-size: clamp(27px, 5vw, 46px); letter-spacing: -.04em; }
.intro { margin: 12px 0 30px; color: #686174; line-height: 1.7; }
.suggestions { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.suggestions button {
  position: relative; display: flex; flex-direction: column; gap: 10px; padding: 14px;
  border: 1px solid #e9e4f0; border-radius: 15px; text-align: left; background: #fff; color: #302741;
  transition: background .18s, border-color .18s;
}
.suggestions button:hover { background: #f5efff; border-color: #c5b6ed; }
.suggestion-icon { display: grid; place-items: center; background: #f0eaff; color: #6038ba; width: 29px; height: 29px; border-radius: 8px; }
.suggestions strong, .suggestions small { display: block; line-height: 1.5; }
.suggestions strong { font-size: 14px; }
.suggestions small { color: #6e657b; margin-top: 4px; }
.arrow { position: absolute; top: 15px; right: 14px; color: #74678e; }
.thread { display: flex; flex-direction: column; gap: 25px; width: min(760px, 100%); margin: auto; padding: 32px 20px; }
.message { max-width: 100%; min-width: 0; }
.user { align-self: flex-end; max-width: 85%; border-radius: 18px 18px 5px 18px; background: #ede6fc; padding: 10px 15px; }
.assistant { align-self: flex-start; }
.assistant-name { margin-bottom: 10px; color: #4a3b65; font-size: 14px; }
.message-text { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; }
.markdown-body { white-space: normal; }
.markdown-body > :first-child { margin-top: 0; }
.markdown-body > :last-child { margin-bottom: 0; }
.markdown-body p { margin: 0 0 12px; }
.markdown-body h1, .markdown-body h2, .markdown-body h3 { margin: 20px 0 10px; line-height: 1.4; letter-spacing: 0; }
.markdown-body h1 { font-size: 1.35em; }
.markdown-body h2 { font-size: 1.2em; }
.markdown-body h3 { font-size: 1.08em; }
.markdown-body ul, .markdown-body ol { margin: 8px 0 14px; padding-left: 1.6em; }
.markdown-body li + li { margin-top: 5px; }
.markdown-body li > p { margin: 0; }
.markdown-body a { color: #5e3ab6; overflow-wrap: anywhere; }
.markdown-body blockquote { margin: 12px 0; padding: 2px 14px; border-left: 3px solid #b49ae5; color: #62596f; }
.markdown-body :not(pre) > code { padding: 2px 5px; border-radius: 5px; background: #eee9f7; color: #5b399b; font-size: .9em; }
.markdown-body pre { max-width: 100%; overflow-x: auto; margin: 12px 0; padding: 15px; border-radius: 12px; background: #252331; color: #e6e2f3; line-height: 1.55; }
.markdown-body pre code { display: block; width: max-content; min-width: 100%; font-size: 13px; }
.markdown-body hr { height: 1px; margin: 20px 0; border: 0; background: #e5e1eb; }
.markdown-body table { display: block; max-width: 100%; overflow-x: auto; border-collapse: collapse; margin: 12px 0; }
.markdown-body th, .markdown-body td { border: 1px solid #e3dfeb; padding: 6px 10px; }
.markdown-body th { background: #f2eef9; }
.status { display: inline-flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 10px; background: #eee8fb; color: #55349f; font-size: 14px; overflow-wrap: anywhere; }
.status-after-text { margin-top: 12px; }
.status-time { color: #776b8a; font-size: 12px; font-variant-numeric: tabular-nums; }
.status-spinner { width: 14px; height: 14px; flex-shrink: 0; border: 2px solid #cdbdee; border-top-color: #6844c6; border-radius: 50%; animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.phase-enter-active, .phase-leave-active { transition: opacity .2s, transform .2s; }
.phase-enter-from, .phase-leave-to { opacity: 0; transform: translateY(4px); }
.pulse, .mode i { width: 7px; height: 7px; border-radius: 50%; background: #754fdb; flex-shrink: 0; }
.cursor { display: inline-block; width: 2px; height: 1em; margin-left: 3px; background: #754fdb; vertical-align: -.12em; }
.pulse, .cursor { animation: blink 1s ease-in-out infinite alternate; }
@keyframes blink { to { opacity: .25; } }
.note { color: #695e7a; font-size: 13px; margin: 8px 0 0; overflow-wrap: anywhere; }
.note[role="alert"] { color: #9f303f; }
.composer-area { width: min(760px, 100%); padding: 8px 20px max(8px, env(safe-area-inset-bottom)); margin: 0 auto; }
.composer { border: 1px solid #e3ddec; border-radius: 20px; background: #fff; padding: 13px; box-shadow: 0 8px 26px #33284d0b; }
.composer:focus-within { border-color: #a98de9; }
textarea { width: 100%; display: block; resize: none; border: 0; border-radius: 6px; color: #272437; padding: 5px; line-height: 1.5; background: transparent; }
textarea::placeholder { color: #756d82; }
.composer-bottom, .mode { display: flex; align-items: center; }
.composer-bottom { justify-content: space-between; padding-top: 5px; }
.mode { gap: 6px; border-radius: 20px; color: #574478; background: #f1ebfa; font-size: 12px; padding: 6px 9px; }
.send { width: 40px; height: 40px; border: 0; border-radius: 13px; background: #6b47d1; color: #fff; font-size: 22px; }
.send:hover { background: #5736b7; }
.send:disabled { background: #eae4f4; color: #6d607d; }
.send.stop { font-size: 14px; }
.hint { color: #696174; text-align: center; font-size: 12px; line-height: 1.5; margin: 9px 0 0; }
@media (max-width: 540px) {
  .welcome { padding-top: 25px; }
  .intro { margin-bottom: 18px; }
  .suggestions { grid-template-columns: 1fr; }
  .suggestions button { flex-direction: row; align-items: center; padding: 9px 34px 9px 11px; }
  .suggestions small { margin-top: 1px; }
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: .01ms !important; transition-duration: .01ms !important; }
}
</style>
