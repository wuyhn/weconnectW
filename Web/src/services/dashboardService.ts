import apiClient from './apiClient'
import { DashboardStats, TrendPoint } from '../types'

export const dashboardService = {
  async getStats(): Promise<DashboardStats> {
    try {
      return await apiClient.get<DashboardStats>('/admin/dashboard/stats')
    } catch (error) {
      console.error('Failed to fetch dashboard stats', error)
      return {
        totalUsers: 0,
        totalPosts: 0,
        activePosts: 0,
        totalReviews: 0,
        blockedUsers: 0,
        archivedPosts: 0,
        topInterestTags: [],
      }
    }
  },

  async getTrends(days: number = 7): Promise<TrendPoint[]> {
    try {
      return await apiClient.get<TrendPoint[]>(`/admin/dashboard/trends?days=${days}`)
    } catch (error) {
      console.error('Failed to fetch trend data', error)
      return []
    }
  },
}
