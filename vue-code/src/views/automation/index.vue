<script setup lang="ts">
import {ref,onMounted,onUnmounted} from 'vue'
import {getAccountList} from '@/api/account'
import {getAutomation,saveAutomation,runPolish,polishRecords,getBlacklist,saveBlacklist,deleteBlacklist,type AutomationConfig,type BlacklistEntry,type PolishRecord} from '@/api/automation'
import type {Account} from '@/types'
import {toast} from '@/utils/toast'
import {showConfirm} from '@/utils/confirm'
const accounts=ref<Account[]>([]), accountId=ref<number|null>(null)
const config=ref<AutomationConfig|null>(null), loading=ref(false), busy=ref(false)
const tab=ref('polish'), records=ref<PolishRecord[]>([]), entries=ref<BlacklistEntry[]>([]), page=ref(1), total=ref(0)
const blank=()=>({id:undefined as number|undefined,scope:'account',buyerId:'',buyerName:'',reason:'',goodsId:'',enabled:true})
const form=ref(blank()), editing=ref(false)
let generation=0, timer:ReturnType<typeof setInterval>|undefined
const date=(v:number|string)=>Number(v)>0?new Date(Number(v)).toLocaleString():'—'
async function refresh(){
  if(!accountId.value)return
  const id=accountId.value, requestedPage=page.value
  const [c,r,b]=await Promise.all([getAutomation(id),polishRecords(id,requestedPage),getBlacklist(id)])
  if(accountId.value!==id||page.value!==requestedPage)return
  if(config.value&&c.data)Object.assign(config.value,{polishResult:c.data.polishResult,nextPolishAt:c.data.nextPolishAt,lastPolishAt:c.data.lastPolishAt,runningUntil:c.data.runningUntil})
  records.value=r.data?.records||[];total.value=r.data?.total||0;entries.value=b.data||[]
}
async function load(){
  const version=++generation;config.value=null;entries.value=[];records.value=[];page.value=1;editing.value=false
  if(!accountId.value)return
  loading.value=true
  try{const r=await getAutomation(accountId.value);if(version!==generation)return;config.value=r.data||null;await refresh()}catch{}finally{if(version===generation)loading.value=false}
}
async function save(){if(!config.value)return;busy.value=true;try{const r=await saveAutomation({...config.value});config.value=r.data||null;toast.success('设置已保存')}catch{}finally{busy.value=false}}
async function polish(){if(!accountId.value)return;busy.value=true;try{await runPolish(accountId.value);toast.success('已提交擦亮任务');await refresh()}catch{}finally{busy.value=false}}
function edit(entry?:BlacklistEntry){form.value=entry?{id:entry.id,scope:entry.xianyu_account_id===null?'global':'account',buyerId:entry.buyer_id,buyerName:entry.buyer_name||'',reason:entry.reason||'',goodsId:entry.goods_id||'',enabled:!!entry.enabled}:blank();editing.value=true}
async function saveEntry(){if(!accountId.value)return;busy.value=true;try{await saveBlacklist({...form.value,xianyuAccountId:form.value.scope==='global'?null:accountId.value});editing.value=false;await refresh();toast.success('黑名单已保存')}catch{}finally{busy.value=false}}
async function remove(entry:BlacklistEntry){try{await showConfirm('确定移除买家 '+entry.buyer_id+' 的黑名单限制？','移除黑名单');busy.value=true;await deleteBlacklist(entry.id);await refresh()}catch{}finally{busy.value=false}}
async function flip(delta:number){page.value+=delta;try{await refresh()}catch{}}
onMounted(async()=>{try{const r=await getAccountList();accounts.value=r.data?.accounts||[];accountId.value=accounts.value[0]?.id||null;await load();timer=setInterval(()=>{if(!loading.value&&!busy.value)void refresh().catch(()=>{})},15000)}catch{}})
onUnmounted(()=>{generation++;if(timer)clearInterval(timer)})
</script>
<template>
  <main class="automation-page">
    <header><div><h1>运营与发货保护</h1><p>按账号设置自动擦亮，发货前核实买家与订单。</p></div><select v-model="accountId" :disabled="busy" aria-label="选择闲鱼账号" @change="load"><option v-if="!accounts.length" :value="null">暂无账号</option><option v-for="a in accounts" :key="a.id" :value="a.id">{{a.accountNote||a.unb}}</option></select></header>
    <nav><button v-for="t in [{id:'polish',label:'自动擦亮'},{id:'protection',label:'发货保护'},{id:'blacklist',label:'买家黑名单'}]" :key="t.id" :class="{active:tab===t.id}" @click="tab=t.id">{{t.label}}</button></nav>
    <p v-if="loading" class="empty">正在加载…</p><p v-else-if="!config" class="empty">请先添加闲鱼账号。</p>
    <template v-else>
      <form v-show="tab==='polish'" class="card" @submit.prevent="save">
        <h2>自动擦亮</h2><p class="hint">处理当前账号已同步的在售商品。每件间隔 1 秒，遇到平台错误停止本轮，可在下方查看原因。</p>
        <label class="option"><span>开启自动擦亮<small>首次开启并保存后开始执行，后续按间隔运行。</small></span><input v-model="config.polishEnabled" type="checkbox" :disabled="busy" /></label>
        <label class="field">擦亮间隔（小时）<input v-model.number="config.polishIntervalHours" type="number" min="1" max="720" required :disabled="busy" /><small>可填写 1–720 小时，默认 24 小时。</small></label>
        <div class="status"><p>上次执行：{{date(config.lastPolishAt)}}</p><p>下次执行：{{config.polishEnabled?date(config.nextPolishAt):'已关闭'}}</p><p>执行结果：{{config.polishResult}}</p></div>
        <footer><button class="primary" :disabled="busy" type="submit">保存设置</button><button :disabled="busy||!config.polishEnabled||config.runningUntil>Date.now()" type="button" @click="polish">立即擦亮</button></footer>
      </form>
      <section v-if="tab==='polish'" class="card"><div class="row"><h2>擦亮记录</h2><button @click="refresh().catch(()=>{})">刷新</button></div><div class="table-wrap"><table><thead><tr><th>商品ID</th><th>结果</th><th>说明</th><th>时间</th></tr></thead><tbody><tr v-for="r in records" :key="r.id"><td>{{r.goods_id}}</td><td :class="r.success?'good':'bad'">{{r.success?'成功':'失败'}}</td><td>{{r.message}}</td><td>{{date(r.created_at)}}</td></tr><tr v-if="!records.length"><td colspan="4" class="empty">暂无擦亮记录</td></tr></tbody></table></div><footer><span>共 {{total}} 条 · 第 {{page}} 页</span><button :disabled="page<=1" @click="flip(-1)">上一页</button><button :disabled="page*20>=total" @click="flip(1)">下一页</button></footer></section>
      <form v-show="tab==='protection'" class="card" @submit.prevent="save">
        <h2>发货前检查</h2><p class="hint">命中规则时暂停发货，不扣新卡密、不发送内容。手动重试也会重新检查，原因可在发货记录查看。查询失败会暂停发货，核实后再重试。</p>
        <label class="option"><span>黑名单拦截<small>优先检查通用和当前账号的黑名单，按买家用户 ID 精确匹配。</small></span><input v-model="config.blacklistEnabled" type="checkbox" /></label>
        <label class="option"><span>买家被评价数保护<small>买家被评价总数小于或等于阈值时暂停。</small></span><input v-model="config.blockLowCredit" type="checkbox" /></label>
        <label v-if="config.blockLowCredit" class="field">被评价数阈值<input v-model.number="config.ratingThreshold" type="number" min="0" max="1000000" required /></label>
        <label class="option"><span>当前账号重复购买保护<small>买家在当前账号有其他有效历史订单时暂停。</small></span><input v-model="config.blockRepeatAccount" type="checkbox" /></label>
        <label class="option"><span>跨账号重复购买保护<small>买家在本系统任一账号有其他有效历史订单时暂停。</small></span><input v-model="config.blockRepeatGlobal" type="checkbox" /></label>
        <label class="option"><span>待确认收货保护<small>买家在当前账号的待收货订单达到阈值时暂停。</small></span><input v-model="config.blockUnconfirmed" type="checkbox" /></label>
        <label v-if="config.blockUnconfirmed" class="field">待收货订单阈值<input v-model.number="config.unconfirmedThreshold" type="number" min="1" max="1000" required /></label>
        <label class="option"><span>只统计同一商品<small>用于重复购买和待确认收货保护；关闭后统计所有商品。</small></span><input v-model="config.sameGoodsOnly" type="checkbox" /></label>
        <label class="field">豁免商品 ID<textarea v-model="config.excludedGoods" maxlength="2000" rows="2" placeholder="多个商品用逗号或换行分隔" /><small>豁免信用、重复购买和待收货检查，黑名单仍生效。</small></label>
        <p class="hint">历史订单以本系统已记录的买家订单为范围，发货时再向平台核实状态；不包含尚未记录到本系统的订单。</p><footer><button class="primary" :disabled="busy" type="submit">保存设置</button></footer>
      </form>
      <section v-if="tab==='blacklist'" class="card"><div class="row"><h2>买家黑名单</h2><button class="primary" :disabled="busy" @click="edit()">添加买家</button></div><p class="hint">通用记录对所有开启黑名单拦截的账号生效。昵称仅作备注，必须填写买家用户 ID。最多展示最近 1000 条。</p>
        <form v-if="editing" class="entry-form" @submit.prevent="saveEntry"><h3>{{form.id?'编辑':'添加'}}黑名单</h3><label class="field">范围<select v-model="form.scope"><option value="account">当前账号</option><option value="global">所有账号通用</option></select></label><label class="field">买家用户 ID<input v-model.trim="form.buyerId" required pattern="[0-9]{1,30}" placeholder="买家数字用户 ID" /></label><label class="field">买家昵称（备注）<input v-model.trim="form.buyerName" maxlength="100" /></label><label class="field">仅限制商品 ID（留空为全部）<input v-model.trim="form.goodsId" pattern="[0-9]{0,30}" /></label><label class="field">拦截原因<input v-model.trim="form.reason" maxlength="200" /></label><label><input v-model="form.enabled" type="checkbox" /> 启用</label><footer><button class="primary" type="submit" :disabled="busy">保存黑名单</button><button type="button" @click="editing=false">取消</button></footer></form>
        <div class="table-wrap"><table><thead><tr><th>买家</th><th>范围</th><th>商品</th><th>原因</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="b in entries" :key="b.id"><td>{{b.buyer_name||'未备注'}}<small>{{b.buyer_id}}</small></td><td>{{b.xianyu_account_id===null?'所有账号通用':'当前账号'}}</td><td>{{b.goods_id||'全部商品'}}</td><td>{{b.reason||'—'}}</td><td>{{b.enabled?'启用':'停用'}}</td><td><button :disabled="busy" @click="edit(b)">编辑</button><button :disabled="busy" @click="remove(b)">移除</button></td></tr><tr v-if="!entries.length"><td colspan="6" class="empty">暂无黑名单</td></tr></tbody></table></div>
      </section>
    </template>
  </main>
</template>
<style scoped>
.automation-page{max-width:1100px;margin:0 auto;padding:24px;color:var(--text-primary,#263248)}header,.row,footer{display:flex;align-items:center;justify-content:space-between;gap:16px}header{margin-bottom:24px}h1{font-size:24px;margin:0 0 8px}h2{font-size:18px;margin:0 0 12px}h3{font-size:16px}p{line-height:1.7;margin:6px 0}.hint,small,header p{color:var(--text-secondary,#64748b);font-size:13px}small{display:block;margin-top:5px}nav{display:flex;gap:8px;margin-bottom:20px}button,select,input:not([type=checkbox]),textarea{font:inherit;border:1px solid var(--border-color,#dbe1ea);border-radius:8px;padding:10px 12px;background:var(--bg-primary,#fff);color:inherit}button{cursor:pointer}button:disabled{opacity:.5;cursor:not-allowed}.primary,nav .active{background:#4f46e5;color:white;border-color:#4f46e5}.card{border:1px solid var(--border-color,#e2e8f0);background:var(--bg-primary,#fff);border-radius:14px;padding:24px;margin-bottom:20px}.option{display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--border-color,#edf0f5);padding:18px 0;gap:20px}.option input{width:20px;height:20px;accent-color:#4f46e5}.field{display:grid;gap:8px;margin:18px 0}.field input[type=number]{max-width:200px}.status,.entry-form{background:var(--bg-secondary,#f5f7fb);padding:16px;border-radius:10px;margin:20px 0}footer{margin-top:20px;justify-content:flex-end;flex-wrap:wrap}.table-wrap{overflow-x:auto}table{width:100%;border-collapse:collapse;text-align:left;font-size:13px}td,th{padding:12px 10px;border-bottom:1px solid var(--border-color,#edf0f5);overflow-wrap:anywhere;min-width:90px}td button{padding:5px 8px;margin-right:4px}.good{color:#16845b}.bad{color:#c2413b}.empty{text-align:center;padding:32px;color:#64748b}@media(max-width:640px){.automation-page{padding:12px}header{align-items:stretch;flex-direction:column}.card{padding:16px}nav{flex-wrap:wrap}}
</style>
