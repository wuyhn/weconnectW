import apiClient from './apiClient'
import { Report, PaginationParams, PaginatedResponse, ReportFilter } from '../types'

/**
 * Report Admin Service
 *
 * Real API endpoints:
 * - GET /admin/reports (list all)
 * - GET /admin/reports/:id (detail)
 * - PUT /admin/reports/:id/status (update status)
 */

export const reportAdminService = {
  /**
   * Get paginated list of reports
   * GET /admin/reports
   */
  async getReports(
    params: PaginationParams,
    filter?: ReportFilter
  ): Promise<PaginatedResponse<Report>> {
    try {
      console.log('[ReportService] Fetching reports from /admin/reports...')
      const reports = await apiClient.get<Report[]>('/admin/reports')
      console.log('[ReportService] Fetched', reports?.length ?? 0, 'reports', reports)

      let filtered = [...reports]

      // Apply client-side filters
      if (filter?.search) {
        const search = filter.search.toLowerCase()
        filtered = filtered.filter(
          (r) =>
            (r.reason || '').toLowerCase().includes(search) ||
            (r.description || '').toLowerCase().includes(search) ||
            (r.reporterName || '').toLowerCase().includes(search)
        )
      }

      if (filter?.targetType) {
        filtered = filtered.filter((r) => r.targetType === filter.targetType)
      }

      if (filter?.status) {
        filtered = filtered.filter((r) => r.status === filter.status)
      }

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
      console.error('Failed to fetch reports from API', error)
      return { data: [], total: 0, page: params.page, pageSize: params.pageSize }
    }
  },

  /**
   * Get single report by ID
   * GET /admin/reports/:id
   */
  async getReport(id: number): Promise<Report> {
    return apiClient.get<Report>(`/admin/reports/${id}`)
  },

  /**
   * Update report status
   * PUT /admin/reports/:id/status
   */
  async updateReportStatus(id: number, status: string): Promise<void> {
    await apiClient.put<void>(`/admin/reports/${id}/status`, { status })
  },

  /**
   * Admin xử lý report với hành động cụ thể
   * POST /admin/reports/:id/resolve
   * Actions: WARN, HIDE_POST, DELETE_POST, BLOCK_USER, DELETE_USER, NO_VIOLATION
   */
  async resolveReport(id: number, action: string): Promise<void> {
    try {
      await apiClient.postRAW<any>(`/admin/reports/${id}/resolve`, { action })
    } catch (error: any) {
      const msg = error.response?.data?.message || error.message || 'Không thể xử lý report'
      throw new Error(msg)
    }
  },
}
