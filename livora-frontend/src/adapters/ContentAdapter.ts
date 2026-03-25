export interface SafeContentItem {
  id: string
  title: string
  description: string
  thumbnailUrl: string
  mediaUrl: string
  creatorUsername: string
  createdAt: string
}

export function adaptContentItem(data: any): SafeContentItem {
  return {
    id: String(data?.id ?? ""),
    title: String(data?.title ?? ""),
    description: String(data?.description ?? ""),
    thumbnailUrl: String(data?.thumbnailUrl ?? ""),
    mediaUrl: String(data?.mediaUrl ?? ""),
    creatorUsername: String(data?.creator?.username ?? ""),
    createdAt: String(data?.createdAt ?? "")
  }
}

export function adaptContentItems(data: any[]): SafeContentItem[] {
  if (!Array.isArray(data)) return []

  return data.map(adaptContentItem)
}
