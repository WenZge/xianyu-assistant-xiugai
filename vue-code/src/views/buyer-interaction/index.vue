<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { getAccountList } from '@/api/account'
import { getInteractionConfig, saveInteractionConfig, getInteractionRecords, retryInteraction, type BuyerInteractionConfig, type InteractionRecord } from '@/api/buyer-interaction'
import type { Account } from '@/types'
import { showSuccess } from '@/utils'

const accounts = ref<Account[]>([])
const accountId = ref<number | null>(null)
const config = ref<BuyerInteractionConfig | null>(null)
const records = ref<InteractionRecord[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const saving = ref(false)
const retryingId = ref<number | null>(null)
const actionNames = { RATE: '评价买家', FLOWER: '索要小红花', THANKS: '收货致谢' }
const states: Record<number, string> = { 0: '等待执行', 1: '成功', 2: '执行中', 3: '已跳过', '-1': '失败' }
let timer: ReturnType<typeof setInterval> | undefined
let generation = 0

async function loadRecords() {
  if (!accountId.value) return
  const current = accountId.value
  const currentPage = page.value
  const response = await getInteractionRecords(current, currentPage)
  if (current !== accountId.value || currentPage !== page.value) return
  records.value = response.data?.records || []
  total.value = response.data?.total || 0
}
async function loadAccount() {
  const version = ++generation
  config.value = null
  records.value = []
  total.value = 0
  page.value = 1
  if (!accountId.value) return
  loading.value = true
  try {
    const response = await getInteractionConfig(accountId.value)
    if (version !== generation) return
    config.value = response.data || null
    await loadRecords()
  } catch { /* 请求层展示错误 */ }
  finally { if (version === generation) loading.value = false }
}
async function save() {
  if (!config.value) return
  saving.value = true
  try {
    await saveInteractionConfig({ ...config.value })
    showSuccess('买家互动设置已保存')
  } catch { /* 请求层展示错误 */ }
  finally { saving.value = false }
}
async function retry(record: InteractionRecord) {
  if (!accountId.value) return
  retryingId.value = record.id
  try { await retryInteraction(accountId.value, record.id); await loadRecords(); showSuccess('已加入重试队列') }
  catch { /* 请求层展示错误 */ }
  finally { retryingId.value = null }
}
async function changePage(delta: number) { page.value += delta; try { await loadRecords() } catch { /* 请求层展示错误 */ } }
onMounted(async () => {
  try {
    const response = await getAccountList()
    accounts.value = response.data?.accounts || []
    accountId.value = accounts.value[0]?.id || null
    await loadAccount()
    timer = setInterval(() => { if (!loading.value) void loadRecords().catch(() => {}) }, 15000)
  } catch { /* 请求层展示错误 */ }
})
onUnmounted(() => { generation++; if (timer) clearInterval(timer) })
</script>

<template>
  <div class="interaction-page">
    <header class="page-heading">
      <div><h1>买家互动</h1><p>确认收货后，自动完成评价、求小红花与致谢。</p></div>
      <select v-model="accountId" :disabled="saving" aria-label="选择闲鱼账号" @change="loadAccount">
        <option v-if="!accounts.length" :value="null">暂无账号</option>
        <option v-for="account in accounts" :key="account.id" :value="account.id">{{ account.accountNote || account.unb }}</option>
      </select>
    </header>
    <div v-if="loading" class="empty">正在加载账号设置…</div>
    <div v-else-if="!config" class="empty">请先在「闲鱼账号」中添加账号。</div>
    <template v-else>
      <form class="settings-card" @submit.prevent="save">
        <div class="card-heading"><h2>收货后自动互动</h2><span>当前账号独立生效</span></div>
        <p class="hint">三项开关默认关闭。保存后处理新完成的卖出订单；每个订单的每项互动仅执行一次。失败记录可在核实平台状态后重试。</p>
        <label class="option"><span><strong>评价买家</strong><small>核实交易成功后，自动给买家好评。</small></span><input v-model="config.autoRate" type="checkbox" role="switch" :disabled="saving" /></label>
        <label class="text-label" for="rate-content">好评文案</label>
        <textarea id="rate-content" v-model="config.rateContent" required maxlength="200" rows="3" :disabled="saving" />
        <div class="counter">{{ config.rateContent.length }} / 200</div>
        <label class="option"><span><strong>索要小红花</strong><small>平台允许求花时，自动向买家发送小红花请求。</small></span><input v-model="config.autoFlower" type="checkbox" role="switch" :disabled="saving" /></label>
        <label class="option"><span><strong>收货致谢</strong><small>通过当前账号的聊天连接发送感谢消息。</small></span><input v-model="config.autoThanks" type="checkbox" role="switch" :disabled="saving" /></label>
        <label class="text-label" for="thanks-content">致谢文案</label>
        <textarea id="thanks-content" v-model="config.thanksContent" required maxlength="200" rows="3" :disabled="saving" />
        <div class="counter">{{ config.thanksContent.length }} / 200</div>
        <div class="form-footer"><label>漏收消息补查间隔 <select v-model="config.pollIntervalSeconds" :disabled="saving"><option :value="300">5 分钟</option><option :value="900">15 分钟</option><option :value="1800">30 分钟</option><option :value="7200">2 小时</option><option :value="86400">24 小时</option></select></label><button class="primary" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存设置' }}</button></div>
      </form>
      <section class="records-card">
        <div class="card-heading"><h2>互动记录</h2><button type="button" @click="loadRecords().catch(() => {})">刷新</button></div>
        <div class="table-wrap"><table><thead><tr><th>订单</th><th>互动</th><th>状态</th><th>结果</th><th>时间</th><th>操作</th></tr></thead><tbody>
          <tr v-for="record in records" :key="record.id"><td>{{ record.orderId }}</td><td>{{ actionNames[record.action] }}</td><td><span :class="['status', { success: record.state === 1, failed: record.state === -1 }]">{{ states[record.state] || '未知' }}</span></td><td class="result">{{ record.resultMessage || '等待平台订单状态就绪' }}</td><td>{{ new Date(record.updatedAt).toLocaleString() }}</td><td><button v-if="record.state === -1" :disabled="retryingId !== null" @click="retry(record)">{{ retryingId === record.id ? '提交中…' : '重试' }}</button><span v-else>—</span></td></tr>
          <tr v-if="!records.length"><td colspan="6" class="empty">暂无互动记录，开启后等待买家确认收货。</td></tr>
        </tbody></table></div>
        <footer class="pagination"><span>共 {{ total }} 条 · 第 {{ page }} 页</span><button :disabled="page <= 1" @click="changePage(-1)">上一页</button><button :disabled="page * 20 >= total" @click="changePage(1)">下一页</button></footer>
      </section>
    </template>
  </div>
</template>

<style scoped>
.interaction-page{padding:24px;max-width:1320px;margin:0 auto;color:#1c1c1e}.page-heading,.card-heading,.form-footer,.pagination{display:flex;align-items:center;justify-content:space-between;gap:16px}.page-heading{margin-bottom:24px}h1{font-size:26px;margin:0 0 8px}h2{font-size:18px;margin:0}.page-heading p,.hint,small,.counter,.card-heading>span{color:#6e7079;font-size:13px}.settings-card,.records-card{background:rgba(255,255,255,.85);border:1px solid #e8e9ed;border-radius:16px;padding:24px;margin-bottom:24px}.hint{line-height:1.8;margin:16px 0}.option{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:18px 0;border-top:1px solid #edeef1}.option strong{font-size:15px}.option small{display:block;margin-top:6px}.option input{appearance:none;position:relative;flex-shrink:0;width:42px;height:24px;border:0;border-radius:20px;background:#d9dbe4;cursor:pointer;transition:background .2s}.option input::after{content:"";position:absolute;top:3px;left:3px;width:18px;height:18px;border-radius:50%;background:white;box-shadow:0 1px 3px #0002;transition:transform .2s}.option input:checked{background:#555bd5}.option input:checked::after{transform:translateX(18px)}.option input:focus-visible{outline:3px solid #a7b4ff;outline-offset:3px}.text-label{display:block;font-size:13px;margin:8px 0}textarea{width:100%;box-sizing:border-box;padding:12px;resize:vertical;border:1px solid #dfe1e8;border-radius:10px;font:inherit;font-size:14px}.counter{text-align:right;margin:5px 0 12px}.form-footer{margin-top:20px;flex-wrap:wrap}select,button{border:1px solid #dfe1e8;border-radius:8px;padding:9px 13px;background:white;color:inherit;font:inherit;font-size:13px}button{cursor:pointer}button:disabled{opacity:.5;cursor:default}.primary{background:#555bd5;color:white;border-color:#555bd5}.table-wrap{overflow-x:auto;margin-top:18px}table{border-collapse:collapse;width:100%;font-size:13px;text-align:left}th,td{padding:13px 12px;border-bottom:1px solid #edeef1;white-space:nowrap}th{color:#737681;font-weight:500}.result{white-space:normal;min-width:180px;max-width:360px;overflow-wrap:anywhere}.empty{text-align:center;padding:40px;color:#777}.status{color:#787b85}.success{color:#16855b}.failed{color:#c33c43}.pagination{justify-content:flex-end;font-size:13px;margin-top:18px}.pagination>span{margin-right:auto;color:#777}@media(max-width:700px){.interaction-page{padding:14px}.page-heading{align-items:flex-start;flex-direction:column}.settings-card,.records-card{padding:16px}.card-heading{flex-wrap:wrap}.form-footer{align-items:flex-start;flex-direction:column}}
</style>
