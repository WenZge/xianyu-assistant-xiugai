import { request } from '@/utils/request'
export interface AutomationConfig {
  xianyuAccountId: number; polishEnabled: boolean; polishIntervalHours: number; nextPolishAt: number; lastPolishAt: number; runningUntil: number; polishResult: string
  blacklistEnabled: boolean; blockLowCredit: boolean; ratingThreshold: number; blockRepeatAccount: boolean; blockRepeatGlobal: boolean; sameGoodsOnly: boolean; blockUnconfirmed: boolean; unconfirmedThreshold: number; excludedGoods: string
}
export interface BlacklistEntry { id: number; xianyu_account_id: number | null; buyer_id: string; buyer_name: string; reason: string; goods_id: string; enabled: number }
export interface PolishRecord { id: number; goods_id: string; success: number; message: string; created_at: number }
export const getAutomation = (xianyuAccountId: number) => request<AutomationConfig>({url:'/automation/config', method:'GET',params:{xianyuAccountId}})
export const saveAutomation = (data: AutomationConfig) => request<AutomationConfig>({url:'/automation/config',method:'POST',data})
export const runPolish = (xianyuAccountId: number) => request({url:'/automation/polish',method:'POST',params:{xianyuAccountId}})
export const polishRecords = (xianyuAccountId: number,page: number) => request<{records:PolishRecord[];total:number}>({url:'/automation/polish-records',method:'GET',params:{xianyuAccountId,page}})
export const getBlacklist = (xianyuAccountId: number) => request<BlacklistEntry[]>({url:'/automation/blacklist',method:'GET',params:{xianyuAccountId}})
export const saveBlacklist = (data: {id?:number;xianyuAccountId:number|null;buyerId:string;buyerName:string;reason:string;goodsId:string;enabled:boolean}) => request({url:'/automation/blacklist',method:'POST',data})
export const deleteBlacklist = (id:number) => request({url:'/automation/blacklist/'+id,method:'DELETE'})
