export interface SafePost {
  id: string
  caption: string
  likeCount: number
  commentCount: number
  mediaUrls: string[]
  creatorUsername: string
  createdAt: string
}

export function adaptPost(data: any): SafePost {

  return {
    id: String(data?.id ?? ""),
    caption: String(data?.caption ?? ""),
    likeCount: Number(data?.likeCount ?? 0),
    commentCount: Number(data?.commentCount ?? 0),
    mediaUrls: Array.isArray(data?.mediaUrls) ? data.mediaUrls : [],
    creatorUsername: String(data?.creator?.username ?? ""),
    createdAt: String(data?.createdAt ?? "")
  }

}

export function adaptPosts(data: any[]): SafePost[] {

  if (!Array.isArray(data)) return []

  return data.map(adaptPost)

}
