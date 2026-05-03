import { collection, getCountFromServer, query, where, Timestamp } from 'firebase/firestore'
import { db } from '../lib/firebase'
import { DashboardStats } from '../types'

export const dashboardService = {
  /**
   * Get dashboard statistics by counting Firestore documents
   */
  async getStats(): Promise<DashboardStats> {
    try {
      const now = Timestamp.now()

      const [
        totalUsersSnap,
        blockedUsersSnap,
        totalPostsSnap,
        archivedPostsSnap,
      ] = await Promise.all([
        getCountFromServer(query(collection(db, 'users'), where('role', '!=', 1))),
        getCountFromServer(query(collection(db, 'users'), where('isBlocked', '==', true))),
        getCountFromServer(collection(db, 'posts')),
        getCountFromServer(query(collection(db, 'posts'), where('archived', '==', true))),
      ])

      return {
        totalUsers: totalUsersSnap.data().count,
        totalPosts: totalPostsSnap.data().count,
        totalReviews: 0, // Chưa implement
        blockedUsers: blockedUsersSnap.data().count,
        archivedPosts: archivedPostsSnap.data().count,
      }
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
   * Trend data (client-side simulation)
   * TODO: Replace with real Firestore query on timestamps
   */
  async getTrendData(): Promise<any[]> {
    await new Promise((resolve) => setTimeout(resolve, 200))
    const data = []
    for (let i = 6; i >= 0; i--) {
      const date = new Date()
      date.setDate(date.getDate() - i)
      data.push({
        date: date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
        users: Math.floor(Math.random() * 20) + 10,
        posts: Math.floor(Math.random() * 25) + 5,
        reviews: 0,
      })
    }
    return data
  },

  async getStatsByCategory(): Promise<any> {
    return {
      postsByInterestTag: [],
      usersByRole: [],
    }
  },
}
