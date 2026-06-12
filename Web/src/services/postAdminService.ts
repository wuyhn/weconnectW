import apiClient from './apiClient'
import { Post, PaginationParams, PaginatedResponse } from '../types'
import { matchesSearchQuery } from '../utils/text'

/**
 * Post Admin Service
 *
 * Real API endpoints:
 * - GET /admin/posts (list all)
 * - GET /admin/posts/:id (detail)
 * - PUT /admin/posts/:id/archive
 * - PUT /admin/posts/:id/unarchive
 * - DELETE /admin/posts/:id
 */

export const postAdminService = {
  /**
   * Get paginated list of posts
   * GET /admin/posts → client-side filter + paginate
   */
  async getPosts(
    params: PaginationParams,
    filter?: { search?: string; archived?: boolean | null }
  ): Promise<PaginatedResponse<Post>> {
    try {
      const posts = await apiClient.get<Post[]>('/admin/posts')

      let filtered = [...posts]

      // Apply client-side filters
      if (filter?.search) {
        filtered = filtered.filter((p) =>
          matchesSearchQuery(
            [
              p.content,
            ],
            filter.search
          )
        )
      }

      if (filter?.archived !== null && filter?.archived !== undefined) {
        filtered = filtered.filter((p) => p.archived === filter.archived)
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
      console.error('Failed to fetch posts from API', error)
      return { data: [], total: 0, page: params.page, pageSize: params.pageSize }
    }
  },

  /**
   * Get single post by ID
   * GET /admin/posts/:id
   */
  async getPost(id: number): Promise<Post> {
    return apiClient.get<Post>(`/admin/posts/${id}`)
  },

  /**
   * Archive post
   * PUT /admin/posts/:id/archive
   */
  async archivePost(id: number): Promise<Post> {
    return apiClient.put<Post>(`/admin/posts/${id}/archive`)
  },

  /**
   * Unarchive post
   * PUT /admin/posts/:id/unarchive
   */
  async unarchivePost(id: number): Promise<Post> {
    return apiClient.put<Post>(`/admin/posts/${id}/unarchive`)
  },

  /**
   * Delete post
   * DELETE /admin/posts/:id
   */
  async deletePost(id: number): Promise<void> {
    await apiClient.delete<void>(`/admin/posts/${id}`)
  },

  /**
   * Update post (not yet supported by backend)
   */
  async updatePost(_id: number, _data: Partial<Post>): Promise<Post> {
    // TODO: implement when backend supports PUT /admin/posts/:id
    throw new Error('Update post not yet implemented')
  },

  /**
   * Get recent posts (limit)
   */
  async getRecentPosts(limit: number = 5): Promise<Post[]> {
    try {
      const posts = await apiClient.get<Post[]>('/admin/posts')
      return [...posts]
        .sort(
          (a, b) =>
            new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        )
        .slice(0, limit)
    } catch {
      return []
    }
  },
}
