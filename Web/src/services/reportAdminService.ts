import apiClient from './apiClient'
import { Report, PaginationParams, PaginatedResponse, ReportFilter, PostTargetInfo, UserTargetInfo } from '../types'
import { cleanTagText, matchesSearchQuery } from '../utils/text'

const parseDateMillis = (value: unknown) => {
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value as number[]
    if (!year || !month || !day) return 0
    const time = new Date(year, month - 1, day, hour, minute, second).getTime()
    return Number.isFinite(time) ? time : 0
  }

  if (!value) return 0

  if (typeof value === 'string') {
    const trimmed = value.trim()
    const viDate = trimmed.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})(?:\s*[·,-]?\s*(\d{1,2}):(\d{2}))?/)
    if (viDate) {
      const [, day, month, year, hour = '0', minute = '0'] = viDate
      const time = new Date(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute)).getTime()
      return Number.isFinite(time) ? time : 0
    }
  }

  const time = new Date(value as string | number | Date).getTime()
  return Number.isFinite(time) ? time : 0
}

const getTargetSearchText = (report: Report) => {
  if (report.targetType === 'USER') {
    const target = report.targetInfo as UserTargetInfo | undefined
    return [report.targetName, target?.fullName, target?.email].filter(Boolean).join(' ')
  }

  const target = report.targetInfo as PostTargetInfo | undefined
  return [
    report.targetName,
    target?.content,
    target?.interestTag,
    cleanTagText(target?.interestTag),
    target?.location,
    target?.authorName,
    target?.authorId,
  ].filter(Boolean).join(' ')
}

const statusSearchText: Record<Report['status'], string> = {
  PENDING: 'chờ xử lý pending',
  VALID: 'đã xác nhận hợp lệ valid vi phạm',
  REJECTED: 'đã từ chối rejected không vi phạm',
}

export const reportAdminService = {
  async getReports(
    params: PaginationParams,
    filter?: ReportFilter
  ): Promise<PaginatedResponse<Report>> {
    try {
      const reports = await apiClient.get<Report[]>('/admin/reports')

      let filtered = [...reports]

      if (filter?.search) {
        filtered = filtered.filter((report) => {
          const targetTypeText = report.targetType === 'USER' ? 'nguoi dung user' : 'bai viet post'
          return matchesSearchQuery(
            [
              report.id,
              `report ${report.id}`,
              report.reporterName,
              report.reporterId,
              `user ${report.reporterId}`,
              report.targetId,
              `${report.targetType.toLowerCase()} ${report.targetId}`,
              report.targetName,
              report.reason,
              report.description,
              report.status,
              statusSearchText[report.status],
              targetTypeText,
              getTargetSearchText(report),
            ],
            filter.search
          )
        })
      }

      if (filter?.targetType) {
        filtered = filtered.filter((report) => report.targetType === filter.targetType)
      }

      if (filter?.statusGroup === 'OPEN') {
        filtered = filtered.filter((report) => report.status === 'PENDING')
      } else if (filter?.statusGroup === 'CLOSED') {
        filtered = filtered.filter((report) => report.status === 'VALID' || report.status === 'REJECTED')
      } else if (filter?.status) {
        filtered = filtered.filter((report) => report.status === filter.status)
      }

      filtered.sort((a, b) => parseDateMillis(b.createdAt) - parseDateMillis(a.createdAt))

      const start = (params.page - 1) * params.pageSize
      const end = start + params.pageSize

      return {
        data: filtered.slice(start, end),
        total: filtered.length,
        page: params.page,
        pageSize: params.pageSize,
      }
    } catch (error) {
      console.error('Failed to fetch reports from API', error)
      throw error
    }
  },

  async getReport(id: number): Promise<Report> {
    return apiClient.get<Report>(`/admin/reports/${id}`)
  },

  // Xác nhận báo cáo hợp lệ (VALID) — endpoint cũ, giữ lại để tương thích
  async approveReport(id: number, penaltyPoint: number, adminNote?: string): Promise<void> {
    try {
      await apiClient.postRAW<any>(`/admin/reports/${id}/approve`, { penaltyPoint, adminNote })
    } catch (error: any) {
      const msg = error.response?.data?.message || error.message || 'Không thể xác nhận báo cáo'
      throw new Error(msg)
    }
  },

  // Phê duyệt báo cáo theo Mã Vi Phạm (ViolationCode Matrix) — endpoint mới tích hợp 5 bước
  // violationCode : SPAM | INAPPROPRIATE | FRAUD | HARASSMENT | U_OTHER (USER report)
  //               : SPAM_POST | MISLEADING | VULGAR | VIOLATION | BULLYING | P_OTHER (POST report)
  // customPenalty : chỉ truyền khi violationCode = U_OTHER / P_OTHER, khoảng [0, 50]
  // adminNote     : bắt buộc ≥ 10 ký tự khi violationCode = U_OTHER / P_OTHER
  async approveViolation(
    id: number,
    violationCode: string,
    customPenalty?: number,
    adminNote?: string,
  ): Promise<{
    reportId: number
    violationCode: string
    penaltyPoint: number
    targetUserId: number
    hiddenPostId: number | null
    newReputationScore: number
    userStatus: string
    userViolationCount: number
  }> {
    try {
      const res = await apiClient.postRAW<any>(`/admin/reports/${id}/approve-violation`, {
        violationCode,
        customPenalty,
        adminNote,
      })
      return res.result
    } catch (error: any) {
      const msg = error.response?.data?.message || error.message || 'Không thể xác nhận báo cáo'
      throw new Error(msg)
    }
  },

  // Từ chối báo cáo (REJECTED)
  async rejectReport(id: number): Promise<void> {
    try {
      await apiClient.postRAW<any>(`/admin/reports/${id}/reject`, {})
    } catch (error: any) {
      const msg = error.response?.data?.message || error.message || 'Không thể từ chối báo cáo'
      throw new Error(msg)
    }
  },

  // Phê duyệt báo cáo sai Tag: ẩn bài + trừ 10 điểm + thông báo phạt (1 lần gọi, 3 bước tự động)
  async approveWrongTag(id: number): Promise<void> {
    try {
      await apiClient.put<void>(`/admin/reports/${id}/approve-wrong-tag`)
    } catch (error: any) {
      const msg = error.response?.data?.message || error.message || 'Không thể xử lý báo cáo sai tag'
      throw new Error(msg)
    }
  },

  // Ẩn bài viết được báo cáo
  async hidePost(id: number): Promise<void> {
    try {
      await apiClient.postRAW<any>(`/admin/reports/${id}/hide-post`, {})
    } catch (error: any) {
      const msg = error.response?.data?.message || error.message || 'Không thể ẩn bài viết'
      throw new Error(msg)
    }
  },

  // Xóa bài viết được báo cáo
  async deletePost(id: number): Promise<void> {
    try {
      await apiClient.delete<any>(`/admin/reports/${id}/delete-post`)
    } catch (error: any) {
      const msg = error.response?.data?.message || error.message || 'Không thể xóa bài viết'
      throw new Error(msg)
    }
  },
}
