export const cleanTagText = (value?: string | null): string => {
  const cleaned = (value ?? '')
    .replace(/[\p{Emoji_Presentation}\p{Extended_Pictographic}\uFE0F]/gu, '')
    .replace(/\[\s*([^\]]*?)\s*\]/g, '$1')
    .replace(/\s+/g, ' ')
    .trim()

  return cleaned || 'Khác'
}

export type SearchField = string | number | boolean | null | undefined

export const normalizeSearchText = (value?: string | number | null): string =>
  String(value ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/\u0111/g, 'd')
    .replace(/\u0110/g, 'D')
    .replace(/[^a-zA-Z0-9]+/g, ' ')
    .replace(/\s+/g, ' ')
    .toLowerCase()
    .trim()

const getSearchAliases = (normalized: string) => {
  const aliases = [normalized.replace(/\s+/g, '')]

  for (const match of normalized.matchAll(/\bquan\s+([0-9]+)\b/g)) {
    aliases.push(`q${match[1]}`)
  }

  for (const match of normalized.matchAll(/\bphuong\s+([0-9]+)\b/g)) {
    aliases.push(`p${match[1]}`)
  }

  if (/\btp\s+hcm\b/.test(normalized) || /\bthanh\s+pho\s+ho\s+chi\s+minh\b/.test(normalized)) {
    aliases.push('tphcm', 'hcm', 'ho chi minh')
  }

  return aliases.join(' ')
}

export const getSearchTokens = (query?: string | number | null): string[] =>
  normalizeSearchText(query).split(' ').filter(Boolean)

export const buildSearchIndex = (fields: SearchField[]): string => {
  const normalized = normalizeSearchText(fields.filter((field) => field !== null && field !== undefined).join(' '))
  return `${normalized} ${getSearchAliases(normalized)}`
}

export const matchesSearchQuery = (fields: SearchField[], query?: string | number | null): boolean => {
  const tokens = getSearchTokens(query)
  if (!tokens.length) return true

  const index = buildSearchIndex(fields)
  return tokens.every((token) => index.includes(token))
}
