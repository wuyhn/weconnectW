/**
 * Normalize avatarUrl về dạng có thể load được từ browser.
 *
 * Mobile app (Android) lưu full URL như http://10.0.2.2:8080/uploads/abc.jpg
 * hoặc http://192.168.x.x:8080/uploads/abc.jpg — những URL này không thể
 * load được từ browser web. Hàm này extract /uploads/... để Vite proxy
 * forward đúng về backend local.
 *
 * Các trường hợp được xử lý:
 * - "/uploads/abc.jpg"                           → "/uploads/abc.jpg"
 * - "http://10.0.2.2:8080/uploads/abc.jpg"       → "/uploads/abc.jpg"
 * - "http://192.168.x.x:8080/uploads/abc.jpg"    → "/uploads/abc.jpg"
 * - "http://localhost:8080/uploads/abc.jpg"       → "/uploads/abc.jpg"
 * - "https://firebasestorage.googleapis.com/..."  → unchanged (external CDN)
 * - null / undefined / ""                         → undefined
 */
export const resolveAvatarUrl = (url?: string | null): string | undefined => {
  if (!url || typeof url !== 'string') return undefined
  const trimmed = url.trim()
  if (!trimmed) return undefined

  // Đã là relative path /uploads/...
  if (trimmed.startsWith('/uploads/')) return trimmed

  // Full URL chứa /uploads/ — extract phần path
  const uploadsMatch = trimmed.match(/(\/uploads\/[^?#\s]+)/)
  if (uploadsMatch) return uploadsMatch[1]

  // External URL (Firebase Storage, S3, CDN) — dùng nguyên
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) return trimmed

  return trimmed
}
