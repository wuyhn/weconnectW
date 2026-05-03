import {
  collection,
  getDocs,
  getDoc,
  doc,
  updateDoc,
  deleteDoc,
} from 'firebase/firestore'
import { db } from '../lib/firebase'
import { Report, PaginationParams, PaginatedResponse, ReportFilter } from '../types'

const toReport = (id: string, data: any): Report => ({
  id: id as any,
  reporterId: data.reporterId || data.actorId || '',
  reporterName: data.reporterName || data.actorName || '',
  targetType: data.targetType || (data.postId ? 'POST' : 'USER'),
  targetId: data.targetId || data.postId || data.actorId || '',
  reason: data.reason || '',
  description: data.description || data.message || '',
  status: data.status || 'PENDING',
  adminAction: data.adminAction,
  createdAt: data.createdAt?.toDate
    ? data.createdAt.toDate().toISOString()
    : (data.createdAt || new Date().toISOString()),
  reviewedAt: data.reviewedAt?.toDate
    ? data.reviewedAt.toDate().toISOString()
    : data.reviewedAt,
  reviewedBy: data.reviewedBy,
})

export const reportAdminService = {
  /**
   * Get paginated list of reports from Firestore
   */
  async getReports(
    params: PaginationParams,
    filter?: ReportFilter
  ): Promise<PaginatedResponse<Report>> {
    try {
      const snapshot = await getDocs(collection(db, 'reports'))
      let reports: Report[] = snapshot.docs.map((d) => toReport(d.id, d.data()))

      if (filter?.search) {
        const search = filter.search.toLowerCase()
        reports = reports.filter(
          (r) =>
            (r.reason || '').toLowerCase().includes(search) ||
            (r.description || '').toLowerCase().includes(search) ||
            (r.reporterName || '').toLowerCase().includes(search)
        )
      }

      if (filter?.targetType) {
        reports = reports.filter((r) => r.targetType === filter.targetType)
      }

      if (filter?.status) {
        reports = reports.filter((r) => r.status === filter.status)
      }

      reports.sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      )

      const start = (params.page - 1) * params.pageSize
      return {
        data: reports.slice(start, start + params.pageSize),
        total: reports.length,
        page: params.page,
        pageSize: params.pageSize,
      }
    } catch (error) {
      console.error('Failed to fetch reports', error)
      return { data: [], total: 0, page: params.page, pageSize: params.pageSize }
    }
  },

  /**
   * Get single report by Firestore doc ID
   */
  async getReport(id: string): Promise<Report> {
    const snap = await getDoc(doc(db, 'reports', id))
    if (!snap.exists()) throw new Error('Report not found')
    return toReport(snap.id, snap.data())
  },

  /**
   * Update report status
   */
  async updateReportStatus(id: string, status: string): Promise<void> {
    await updateDoc(doc(db, 'reports', id), {
      status,
      reviewedAt: new Date().toISOString(),
    })
  },

  /**
   * Resolve report with admin action
   * Actions: WARN, HIDE_POST, DELETE_POST, BLOCK_USER, DELETE_USER, NO_VIOLATION
   */
  async resolveReport(id: string, action: string): Promise<void> {
    const reportSnap = await getDoc(doc(db, 'reports', id))
    if (!reportSnap.exists()) throw new Error('Report not found')

    const report = reportSnap.data()

    // Thực hiện hành động tương ứng
    if (action === 'DELETE_POST' && report.postId) {
      try {
        await deleteDoc(doc(db, 'posts', report.postId))
      } catch (e) { console.error('Failed to delete post', e) }
    }

    if (action === 'BLOCK_USER' && report.targetId) {
      try {
        await updateDoc(doc(db, 'users', String(report.targetId)), { isBlocked: true })
      } catch (e) { console.error('Failed to block user', e) }
    }

    if (action === 'DELETE_USER' && report.targetId) {
      try {
        await deleteDoc(doc(db, 'users', String(report.targetId)))
      } catch (e) { console.error('Failed to delete user', e) }
    }

    // Cập nhật trạng thái report
    await updateDoc(doc(db, 'reports', id), {
      status: 'RESOLVED',
      adminAction: action,
      reviewedAt: new Date().toISOString(),
    })
  },
}
