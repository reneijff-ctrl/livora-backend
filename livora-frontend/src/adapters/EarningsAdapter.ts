export interface SafeEarningsOverview {
  totalRevenue: number
  tipRevenue: number
  subscriptionRevenue: number
  ppvRevenue: number
}

export interface SafeEarningEntry {
  id: string
  sourceType: string
  grossAmount: number
  netAmount: number
  createdAt: string
}

export function adaptEarningsOverview(data: any): SafeEarningsOverview {
  return {
    totalRevenue: Number(data?.totalRevenue ?? 0),
    tipRevenue: Number(data?.tipRevenue ?? 0),
    subscriptionRevenue: Number(data?.subscriptionRevenue ?? 0),
    ppvRevenue: Number(data?.ppvRevenue ?? 0)
  }
}

export function adaptEarningEntries(data: any[]): SafeEarningEntry[] {
  if (!Array.isArray(data)) return []

  return data.map(entry => ({
    id: String(entry?.id ?? ""),
    sourceType: String(entry?.sourceType ?? "unknown"),
    grossAmount: Number(entry?.grossAmount ?? 0),
    netAmount: Number(entry?.netAmount ?? 0),
    createdAt: String(entry?.createdAt ?? "")
  }))
}
