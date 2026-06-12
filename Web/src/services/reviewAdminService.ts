import apiClient from './apiClient'
import { UserReview, PaginationParams, PaginatedResponse } from '../types'
import { cleanTagText, matchesSearchQuery } from '../utils/text'

/**
 * Review Admin Service
 *
 * Real API endpoints:
 * - GET /admin/reviews (list all)
 * - GET /admin/reviews/:id (detail)
 */

export type ReviewLabelFilter = 'positive' | 'medium' | 'improve' | null

const parseDateMillis = (value: unknown) => {
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value as number[]
    if (!year || !month || !day) return 0
    const time = new Date(year, month - 1, day, hour, minute, second).getTime()
    return Number.isFinite(time) ? time : 0
  }

  if (!value) return 0

  if (typeof value === 'string') {
    const trimmed = value.trim()
    const viDate = trimmed.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})(?:\s*[·,-]?\s*(\d{1,2}):(\d{2}))?/)
    if (viDate) {
      const [, day, month, year, hour = '0', minute = '0'] = viDate
      const time = new Date(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute)).getTime()
      return Number.isFinite(time) ? time : 0
    }
  }

  const time = new Date(value as string | number | Date).getTime()
  return Number.isFinite(time) ? time : 0
}

const clampRating = (value?: number) => {
  if (typeof value !== 'number' || Number.isNaN(value)) return 0
  return Math.max(0, Math.min(5, value))
}

const getReviewRating = (review: UserReview) => {
  const rating = clampRating(review.rating)
  if (rating > 0) return rating

  const label = (review.reputationLabel || '').toLowerCase()
  if (label.includes('excellent') || label.includes('đáng') || label.includes('dang')) return 5
  if (label.includes('good') || label.includes('nhiệt') || label.includes('nhiet') || label.includes('tích cực')) return 4
  if (label.includes('average') || label.includes('bình') || label.includes('binh') || label.includes('trung')) return 3
  if (label.includes('poor') || label.includes('không') || label.includes('khong') || label.includes('cải thiện')) return 2
  return 0
}

const getReviewLabel = (review: UserReview): Exclude<ReviewLabelFilter, null> => {
  const rating = getReviewRating(review)
  if (rating >= 4) return 'positive'
  if (rating === 3) return 'medium'
  if (rating > 0) return 'improve'
  return 'medium'
}

export const reviewAdminService = {
  /**
   * Get all reviews from backend
   * GET /admin/reviews
   */
  async getReviews(
    params: PaginationParams,
    filter?: { search?: string; label?: ReviewLabelFilter; rating?: number | null; reputationLabel?: string | null }
  ): Promise<PaginatedResponse<UserReview>> {
    try {
      const reviews = await apiClient.get<UserReview[]>('/admin/reviews')

      let filtered = [...reviews]

      if (filter?.search) {
        filtered = filtered.filter((review) =>
          matchesSearchQuery(
            [
              review.id,
              `review ${review.id}`,
              review.comment,
              review.activityName,
              review.interestTag,
              cleanTagText(review.interestTag || review.activityName),
              review.reviewerName,
              review.reviewerId,
              `user ${review.reviewerId}`,
              review.reviewedUserName,
              review.reviewedUserId,
              `user ${review.reviewedUserId}`,
              review.rating,
              getReviewLabel(review),
              review.reputationLabel,
            ],
            filter.search
          )
        )
      }

      if (filter?.label) {
        filtered = filtered.filter((review) => getReviewLabel(review) === filter.label)
      }

      if (filter?.rating) {
        filtered = filtered.filter((review) => Math.round(getReviewRating(review)) === filter.rating)
      }

      if (filter?.reputationLabel) {
        filtered = filtered.filter((review) => review.reputationLabel === filter.reputationLabel)
      }

      filtered.sort((a, b) => parseDateMillis(b.createdAt) - parseDateMillis(a.createdAt))

      const start = (params.page - 1) * params.pageSize
      const end = start + params.pageSize

      return {
        data: filtered.slice(start, end),
        total: filtered.length,
        page: params.page,
        pageSize: params.pageSize,
      }
    } catch (error) {
      console.error('Failed to fetch reviews from API', error)
      throw error
    }
  },

  /**
   * Get single review by ID
   * GET /admin/reviews/:id
   */
  async getReview(id: number): Promise<UserReview> {
    return apiClient.get<UserReview>(`/admin/reviews/${id}`)
  },

  /**
   * Get recent reviews (limit)
   */
  async getRecentReviews(limit: number = 5): Promise<UserReview[]> {
    try {
      const reviews = await apiClient.get<UserReview[]>('/admin/reviews')
      return [...reviews]
        .sort((a, b) => parseDateMillis(b.createdAt) - parseDateMillis(a.createdAt))
        .slice(0, limit)
    } catch {
      return []
    }
  },

  /**
   * Get reputation labels available
   */
  getReputationLabels(): string[] {
    return ['Tích cực', 'Trung bình', 'Cần cải thiện']
  },

  /**
   * Delete review
   * DELETE /admin/reviews/:id
   */
  async deleteReview(id: number): Promise<void> {
    await apiClient.delete<void>(`/admin/reviews/${id}`)
  },
}
