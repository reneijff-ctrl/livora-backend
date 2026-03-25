export function safeRender(value: unknown): string {
  if (value === null || value === undefined) return ''

  if (typeof value === 'string') return value
  if (typeof value === 'number') return Number.isFinite(value) ? String(value) : '0'
  if (typeof value === 'boolean') return value ? 'true' : 'false'

  if (Array.isArray(value)) return value.join(', ')

  if (typeof value === 'object') {
    const obj = value as Record<string, unknown>
    if (obj.username) return String(obj.username)
    if (obj.name) return String(obj.name)
    if (obj.message) return String(obj.message)
    if (obj.content) return String(obj.content)
    if (obj.text) return String(obj.text)
    return ''
  }

  return String(value)
}
