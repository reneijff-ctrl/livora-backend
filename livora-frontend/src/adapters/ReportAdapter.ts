export interface SafeReport {
  id: string
  status: string
  reason: string
  notes: string
  creatorUsername: string
  createdAt: string
}

export function adaptReport(data: any): SafeReport {
  return {
    id: String(data?.id ?? ""),
    status: String(data?.status ?? "OPEN"),
    reason: String(data?.reason ?? ""),
    notes: String(data?.notes ?? ""),
    creatorUsername: String(data?.creator?.username ?? ""),
    createdAt: String(data?.createdAt ?? "")
  }
}

export function adaptReports(data: any[]): SafeReport[] {
  if (!Array.isArray(data)) return []

  return data.map(adaptReport)
}
