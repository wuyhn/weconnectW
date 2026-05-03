import {
  collection,
  getDocs,
  doc,
  updateDoc,
  query,
  where,
  orderBy,
} from 'firebase/firestore'
import { db } from '../lib/firebase'

export interface ReportNotification {
  id: string
  reporterId: string
  reporterName: string
  targetType: 'USER' | 'POST'
  targetId: string
  targetName?: string
  reason: string
  status: 'PENDING' | 'REVIEWED' | 'RESOLVED'
  adminViewed: boolean
  createdAt: string
}

export const notificationService = {
  /**
   * Get all report notifications from Firestore reports collection
   */
  async getReportNotifications(): Promise<ReportNotification[]> {
    try {
      const q = query(
        collection(db, 'reports'),
        orderBy('createdAt', 'desc')
      )
      const snapshot = await getDocs(q)
      return snapshot.docs.map((d) => {
        const data = d.data()
        return {
          id: d.id,
          reporterId: data.reporterId || data.actorId || '',
          reporterName: data.reporterName || data.actorName || '',
          targetType: data.targetType || (data.postId ? 'POST' : 'USER'),
          targetId: data.targetId || data.postId || '',
          targetName: data.targetName,
          reason: data.reason || '',
          status: data.status || 'PENDING',
          adminViewed: data.adminViewed || false,
          createdAt: data.createdAt?.toDate
            ? data.createdAt.toDate().toISOString()
            : (data.createdAt || new Date().toISOString()),
        }
      })
    } catch (error) {
      console.error('Failed to fetch report notifications', error)
      return []
    }
  },

  /**
   * Get count of unread/unviewed reports
   */
  async getUnreadCount(): Promise<number> {
    try {
      const q = query(
        collection(db, 'reports'),
        where('adminViewed', '==', false)
      )
      const snapshot = await getDocs(q)
      return snapshot.size
    } catch {
      return 0
    }
  },

  /**
   * Mark a single report notification as viewed
   */
  async markAsViewed(id: string): Promise<void> {
    await updateDoc(doc(db, 'reports', id), { adminViewed: true })
  },

  /**
   * Mark all report notifications as viewed
   */
  async markAllAsViewed(): Promise<void> {
    try {
      const q = query(collection(db, 'reports'), where('adminViewed', '==', false))
      const snapshot = await getDocs(q)
      await Promise.all(
        snapshot.docs.map((d) => updateDoc(d.ref, { adminViewed: true }))
      )
    } catch (error) {
      console.error('Failed to mark all as viewed', error)
    }
  },
}
