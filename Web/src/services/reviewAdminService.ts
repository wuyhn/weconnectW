import apiClient from './apiClient'
import { UserReview, PaginationParams, PaginatedResponse } from '../types'

/**
 * Review Admin Service
 *
 * Real API endpoints:
 * - GET /admin/reviews (list all)
 * - GET /admin/reviews/:id (detail)
 */

export const reviewAdminService = {
  /**
   * Get all reviews from backend
   * GET /admin/reviews
   */
  async getReviews(
    params: PaginationParams,
    filter?: { search?: string; reputationLabel?: string | null }
  ): Promise<PaginatedResponse<UserReview>> {
    try {
      const reviews = await apiClient.get<UserReview[]>('/admin/reviews')

      let filtered = [...reviews]

      // Apply client-side filters
      if (filter?.search) {
        const search = filter.search.toLowerCase()
        filtered = filtered.filter(
          (r) =>
            (r.comment || '').toLowerCase().includes(search) ||
            (r.activityName || '').toLowerCase().includes(search) ||
            (r.reviewerName || '').toLowerCase().includes(search) ||
            (r.reviewedUserName || '').toLowerCase().includes(search)
        )
      }

      if (filter?.reputationLabel) {
        filtered = filtered.filter((r) => r.reputationLabel === filter.reputationLabel)
      }

      // Paginate
      const start = (params.page - 1) * params.pageSize
      const end = start + params.pageSize

      return {
        data: filtered.slice(start, end),
        total: filtered.length,
        page: params.page,
        pageSize: params.pageSize,
      }
    } catch (error) {
      console.error('Failed to fetch reviews from API, returning empty', error)
      return { data: [], total: 0, page: params.page, pageSize: params.pageSize }
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
      return reviews.slice(0, limit)
    } catch {
      return []
    }
  },

  /**
   * Get reputation labels available
   */
  getReputationLabels(): string[] {
    return ['Đáng tin cậy', 'Nhiệt tình', 'Bình thường', 'Không tốt']
  },

  /**
   * Delete review
   * DELETE /admin/reviews/:id
   */
  async deleteReview(id: number): Promise<void> {
    await apiClient.delete<void>(`/admin/reviews/${id}`)
  },
}
