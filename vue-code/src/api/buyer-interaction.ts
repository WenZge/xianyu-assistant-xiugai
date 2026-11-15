import { request } from '@/utils/request'

export interface BuyerInteractionConfig {
  xianyuAccountId: number
  autoRate: boolean
  autoFlower: boolean
  autoThanks: boolean
  rateContent: string
  thanksContent: string
  pollIntervalSeconds: number
}
export interface InteractionRecord {
  id: number
  orderId: string
  action: 'RATE' | 'FLOWER' | 'THANKS'
  state: number
  content: string
  resultMessage: string
  updatedAt: number
}
export const getInteractionConfig = (xianyuAccountId: number) => request<BuyerInteractionConfig>({ url: '/buyer-interaction/config', method: 'GET', params: { xianyuAccountId } })
export const saveInteractionConfig = (data: BuyerInteractionConfig) => request<BuyerInteractionConfig>({ url: '/buyer-interaction/config', method: 'POST', data })
export const getInteractionRecords = (xianyuAccountId: number, page: number) => request<{ records: InteractionRecord[]; total: number }>({ url: '/buyer-interaction/records', method: 'GET', params: { xianyuAccountId, page, size: 20 } })
export const retryInteraction = (xianyuAccountId: number, recordId: number) => request({ url: '/buyer-interaction/retry', method: 'POST', params: { xianyuAccountId, recordId } })
