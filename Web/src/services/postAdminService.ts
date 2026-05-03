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
import { Post, PaginationParams, PaginatedResponse } from '../types'

const toPost = (id: string, data: any): Post => ({
  id: id as any,
  authorId: data.authorId || '',
  authorName: data.authorName || '',
  content: data.content || '',
  interestTag: data.interestTag || '',
  location: data.location || '',
  imageUrl: data.imageUrl,
  maxMembers: data.maxMembers || 0,
  startTime: data.startTime?.toDate
    ? data.startTime.toDate().toISOString()
    : (data.startTime || ''),
  endTime: data.endTime?.toDate
    ? data.endTime.toDate().toISOString()
    : (data.endTime || ''),
  archived: data.archived || false,
  createdAt: data.createdAt?.toDate
    ? data.createdAt.toDate().toISOString()
    : (data.createdAt || new Date().toISOString()),
})

export const postAdminService = {
  /**
   * Get paginated list of posts from Firestore
   */
  async getPosts(
    params: PaginationParams,
    filter?: { search?: string; archived?: boolean | null }
  ): Promise<PaginatedResponse<Post>> {
    try {
      const snapshot = await getDocs(collection(db, 'posts'))
      const now = new Date().getTime()

      let posts: Post[] = snapshot.docs.map((d) => {
        const p = toPost(d.id, d.data())
        // Tính thực tế: expired nếu endTime < now
        const isExpired = p.endTime ? new Date(p.endTime).getTime() < now : false
        return { ...p, archived: p.archived || isExpired }
      })

      if (filter?.search) {
        const search = filter.search.toLowerCase()
        posts = posts.filter(
          (p) =>
            (p.content || '').toLowerCase().includes(search) ||
            (p.interestTag || '').toLowerCase().includes(search) ||
            (p.location || '').toLowerCase().includes(search)
        )
      }

      if (filter?.archived !== null && filter?.archived !== undefined) {
        posts = posts.filter((p) => p.archived === filter.archived)
      }

      posts.sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      )

      const start = (params.page - 1) * params.pageSize
      return {
        data: posts.slice(start, start + params.pageSize),
        total: posts.length,
        page: params.page,
        pageSize: params.pageSize,
      }
    } catch (error) {
      console.error('Failed to fetch posts', error)
      return { data: [], total: 0, page: params.page, pageSize: params.pageSize }
    }
  },

  /**
   * Get single post by Firestore doc ID
   */
  async getPost(id: string): Promise<Post> {
    const snap = await getDoc(doc(db, 'posts', id))
    if (!snap.exists()) throw new Error('Post not found')
    return toPost(snap.id, snap.data())
  },

  /**
   * Archive post
   */
  async archivePost(id: string): Promise<Post> {
    await updateDoc(doc(db, 'posts', id), { archived: true })
    return this.getPost(id)
  },

  /**
   * Unarchive post
   */
  async unarchivePost(id: string): Promise<Post> {
    await updateDoc(doc(db, 'posts', id), { archived: false })
    return this.getPost(id)
  },

  /**
   * Delete post and its members subcollection
   */
  async deletePost(id: string): Promise<void> {
    // Delete members subcollection docs first
    try {
      const membersSnap = await getDocs(collection(db, 'posts', id, 'members'))
      await Promise.all(membersSnap.docs.map((m) => deleteDoc(m.ref)))
    } catch {
      // ignore if no subcollection
    }
    await deleteDoc(doc(db, 'posts', id))
  },

  async updatePost(_id: string, _data: Partial<Post>): Promise<Post> {
    throw new Error('Update post not yet implemented')
  },

  /**
   * Get most recent posts
   */
  async getRecentPosts(limitCount: number = 5): Promise<Post[]> {
    try {
      const q = query(collection(db, 'posts'), orderBy('createdAt', 'desc'), limit(limitCount))
      const snapshot = await getDocs(q)
      return snapshot.docs.map((d) => toPost(d.id, d.data()))
    } catch {
      return []
    }
  },
}
