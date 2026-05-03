import {
  collection,
  getDocs,
  getDoc,
  doc,
  updateDoc,
  deleteDoc,
  query,
  orderBy,
  limit,
} from 'firebase/firestore'
import { db } from '../lib/firebase'
import { User, PaginationParams, PaginatedResponse } from '../types'

const toUser = (id: string, data: any): User => ({
  id: id as any,
  email: data.email || '',
  fullName: data.fullName || '',
  birthday: data.birthday,
  gender: data.gender,
  avatarUrl: data.avatarUrl,
  bio: data.bio,
  interestTags: data.interestTags || [],
  averageRating: data.averageRating,
  reputationScore: data.reputationScore,
  isBlocked: data.isBlocked || false,
  role: data.role === 1 || data.role === 'admin' ? 1 : 0,
  createdAt: data.createdAt?.toDate
    ? data.createdAt.toDate().toISOString()
    : (data.createdAt || new Date().toISOString()),
  postCount: data.postCount,
})

export const userAdminService = {
  /**
   * Get paginated list of users from Firestore
   */
  async getUsers(
    params: PaginationParams,
    filter?: { search?: string; role?: number | null; isBlocked?: boolean | null }
  ): Promise<PaginatedResponse<User>> {
    try {
      const snapshot = await getDocs(collection(db, 'users'))
      let users: User[] = snapshot.docs.map((d) => toUser(d.id, d.data()))

      // Exclude admin users by default
      users = users.filter((u) => u.role !== 1)

      if (filter?.search) {
        const search = filter.search.toLowerCase()
        users = users.filter(
          (u) =>
            (u.fullName || '').toLowerCase().includes(search) ||
            (u.email || '').toLowerCase().includes(search)
        )
      }

      if (filter?.isBlocked !== null && filter?.isBlocked !== undefined) {
        users = users.filter((u) => u.isBlocked === filter.isBlocked)
      }

      // Sort by createdAt desc
      users.sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      )

      const start = (params.page - 1) * params.pageSize
      return {
        data: users.slice(start, start + params.pageSize),
        total: users.length,
        page: params.page,
        pageSize: params.pageSize,
      }
    } catch (error) {
      console.error('Failed to fetch users', error)
      return { data: [], total: 0, page: params.page, pageSize: params.pageSize }
    }
  },

  /**
   * Get single user by Firestore doc ID
   */
  async getUser(id: string): Promise<User> {
    const snap = await getDoc(doc(db, 'users', id))
    if (!snap.exists()) throw new Error('User not found')
    return toUser(snap.id, snap.data())
  },

  /**
   * Block user → set isBlocked = true
   */
  async blockUser(id: string): Promise<User> {
    await updateDoc(doc(db, 'users', id), { isBlocked: true })
    return this.getUser(id)
  },

  /**
   * Unblock user → set isBlocked = false
   */
  async unblockUser(id: string): Promise<User> {
    await updateDoc(doc(db, 'users', id), { isBlocked: false })
    return this.getUser(id)
  },

  /**
   * Delete user document from Firestore
   */
  async deleteUser(id: string): Promise<void> {
    await deleteDoc(doc(db, 'users', id))
  },

  async updateUser(_id: string, _data: Partial<User>): Promise<User> {
    throw new Error('Update user not yet implemented')
  },

  /**
   * Get most recent users
   */
  async getRecentUsers(limitCount: number = 5): Promise<User[]> {
    try {
      const q = query(collection(db, 'users'), orderBy('createdAt', 'desc'), limit(limitCount * 3))
      const snapshot = await getDocs(q)
      const users = snapshot.docs
        .map((d) => toUser(d.id, d.data()))
        .filter((u) => u.role !== 1)
        .slice(0, limitCount)
      return users
    } catch {
      return []
    }
  },
}
