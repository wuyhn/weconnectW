import apiClient from './apiClient'
import { DashboardStats } from '../types'

/**
 * Dashboard Admin Service
 *
 * Real API endpoint:
 * - GET /admin/dashboard/stats
 *
 * Trend data and category stats are still generated client-side
 * (no backend endpoint yet).
 */

export const dashboardService = {
  /**
   * Get dashboard statistics
   * GET /admin/dashboard/stats
   */
  async getStats(): Promise<DashboardStats> {
    try {
      return await apiClient.get<DashboardStats>('/admin/dashboard/stats')
    } catch (error) {
      console.error('Failed to fetch dashboard stats', error)
      return {
        totalUsers: 0,
        totalPosts: 0,
        totalReviews: 0,
        blockedUsers: 0,
        archivedPosts: 0,
      }
    }
  },

  /**
   * Get stats trend data (for charts)
   * TODO: Replace with real endpoint GET /admin/dashboard/trends
   */
  async getTrendData(): Promise<any[]> {
    await new Promise((resolve) => setTimeout(resolve, 200))

    // Sample trend data for the last 7 days
    const data = []
    for (let i = 6; i >= 0; i--) {
      const date = new Date()
      date.setDate(date.getDate() - i)
      data.push({
        date: date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
        users: Math.floor(Math.random() * 20) + 10,
        posts: Math.floor(Math.random() * 25) + 5,
        reviews: Math.floor(Math.random() * 15) + 3,
      })
    }
    return data
  },

  /**
   * Get stats by category
   * TODO: Replace with real endpoint
   */
  async getStatsByCategory(): Promise<any> {
    await new Promise((resolve) => setTimeout(resolve, 200))
    return {
      postsByInterestTag: [],
      usersByRole: [],
    }
  },
}
