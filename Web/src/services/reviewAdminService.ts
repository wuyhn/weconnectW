import { PaginationParams, PaginatedResponse, UserReview } from '../types'

// Reviews là tính năng chưa được implement trong app Android
// Trả về empty data cho tất cả các endpoints

export const reviewAdminService = {
  async getReviews(
    params: PaginationParams,
    _filter?: { search?: string; reputationLabel?: string | null }
  ): Promise<PaginatedResponse<UserReview>> {
    return { data: [], total: 0, page: params.page, pageSize: params.pageSize }
  },

  async getReview(_id: string): Promise<UserReview> {
    throw new Error('Reviews not yet implemented')
  },

  async getRecentReviews(_limit: number = 5): Promise<UserReview[]> {
    return []
  },

  getReputationLabels(): string[] {
    return ['Đáng tin cậy', 'Nhiệt tình', 'Bình thường', 'Không tốt']
  },

  async deleteReview(_id: string): Promise<void> {
    throw new Error('Reviews not yet implemented')
  },
}
