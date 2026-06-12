import apiClient from './apiClient'
import { User, UserStats, PaginationParams, PaginatedResponse } from '../types'
import { mockUsers } from '../mock/mockData'
import { cleanTagText, matchesSearchQuery } from '../utils/text'

/**
 * User Admin Service
 *
 * Real API endpoints:
 * - GET /admin/users (list all)
 * - GET /admin/users/:id (detail)
 * - PUT /admin/users/:id/block
 * - PUT /admin/users/:id/unblock
 * - DELETE /admin/users/:id
 */

export const userAdminService = {
  /**
   * Get paginated list of users
   * GET /admin/users → client-side filter + paginate
   * Auto-excludes admin users (role !== 1)
   */
  async getUsers(
    params: PaginationParams,
    filter?: { search?: string; role?: number | null; isBlocked?: boolean | null }
  ): Promise<PaginatedResponse<User>> {
    try {
      const users = await apiClient.get<User[]>('/admin/users')

      let filtered = [...users]

      // Exclude admin users by default
      filtered = filtered.filter((u) => u.role !== 1)

      // Apply client-side filters
      if (filter?.search) {
        filtered = filtered.filter((u) =>
          matchesSearchQuery(
            [
              u.fullName,
              u.email,
              u.id,
              `user ${u.id}`,
              u.bio,
              u.gender,
              u.role === 1 ? 'admin quan tri vien' : 'user nguoi dung',
              u.isBlocked ? 'blocked bi khoa' : 'active hoat dong',
              ...(u.interestTags || []).map((tag) => cleanTagText(tag)),
            ],
            filter.search
          )
        )
      }

      if (filter?.role !== null && filter?.role !== undefined) {
        filtered = filtered.filter((u) => u.role === filter.role)
      }

      if (filter?.isBlocked !== null && filter?.isBlocked !== undefined) {
        filtered = filtered.filter((u) => u.isBlocked === filter.isBlocked)
      }

      // Sort by created date descending
      filtered.sort(
        (a, b) =>
          new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      )

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
      console.error('Failed to fetch users from API', error)
      return { data: [], total: 0, page: params.page, pageSize: params.pageSize }
    }
  },

  /**
   * Get single user by ID
   * GET /admin/users/:id
   */
  async getUser(id: number): Promise<User> {
    try {
      return await apiClient.get<User>(`/admin/users/${id}`)
    } catch (error) {
      // Fallback to mock data if API fails
      const user = mockUsers.find((u) => u.id === id)
      if (user) {
        return user
      }
      throw error
    }
  },

  /**
   * Block user
   * PUT /admin/users/:id/block
   */
  async blockUser(id: number): Promise<User> {
    return apiClient.put<User>(`/admin/users/${id}/block`)
  },

  /**
   * Unblock user
   * PUT /admin/users/:id/unblock
   */
  async unblockUser(id: number): Promise<User> {
    return apiClient.put<User>(`/admin/users/${id}/unblock`)
  },

  /**
   * Delete user
   * DELETE /admin/users/:id
   */
  async deleteUser(id: number): Promise<void> {
    await apiClient.delete<void>(`/admin/users/${id}`)
  },

  /**
   * Get user activity stats
   * GET /admin/users/:id/stats
   */
  async getUserStats(id: number): Promise<UserStats> {
    try {
      return await apiClient.get<UserStats>(`/admin/users/${id}/stats`)
    } catch {
      return {
        totalPostsCreated: 0,
        totalActivitiesJoined: 0,
        totalReviewsReceived: 0,
        totalReportsReceived: 0,
        confirmedViolations: 0,
      }
    }
  },

  /**
   * Update user (not yet supported by backend)
   */
  async updateUser(_id: number, _data: Partial<User>): Promise<User> {
    // TODO: implement when backend supports PUT /admin/users/:id
    throw new Error('Update user not yet implemented')
  },

  /**
   * Get recent users (limit)
   */
  async getRecentUsers(limit: number = 5): Promise<User[]> {
    try {
      const users = await apiClient.get<User[]>('/admin/users')
      return [...users]
        .sort(
          (a, b) =>
            new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        )
        .slice(0, limit)
    } catch {
      return []
    }
  },

  /**
   * Get all users raw (including admins) — used for building author maps
   * GET /admin/users (no client-side role filter)
   */
  async getAllUsersRaw(): Promise<User[]> {
    try {
      return await apiClient.get<User[]>('/admin/users')
    } catch {
      return []
    }
  },
}
