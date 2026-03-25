export interface SafeTokenBalance {
  balance: number
}

export interface SafeTokenPackage {
  id: string
  name: string
  tokenAmount: number
  price: number
  currency: string
}

export function adaptTokenBalance(data: any): SafeTokenBalance {
  return {
    balance: Number(data?.balance ?? 0)
  }
}

export function adaptTokenPackages(data: any[]): SafeTokenPackage[] {
  if (!Array.isArray(data)) return []

  return data.map(pkg => ({
    id: String(pkg?.id ?? ""),
    name: String(pkg?.name ?? ""),
    tokenAmount: Number(pkg?.tokenAmount ?? 0),
    price: Number(pkg?.price ?? 0),
    currency: String(pkg?.currency ?? "usd")
  }))
}
