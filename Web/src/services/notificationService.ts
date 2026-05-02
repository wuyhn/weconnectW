import apiClient from './apiClient'

export interface ReportNotification {
    id: number
    reporterId: number
    reporterName: string
    targetType: 'USER' | 'POST'
    targetId: number
    targetName?: string
    reason: string
    status: 'PENDING' | 'REVIEWED' | 'RESOLVED'
    adminViewed: boolean
    createdAt: string
}

export const notificationService = {
    /** Get all report notifications */
    async getReportNotifications(): Promise<ReportNotification[]> {
        return apiClient.get<ReportNotification[]>('/admin/report-notifications')
    },

    /** Get unviewed report count (for badge) */
    async getUnreadCount(): Promise<number> {
        return apiClient.get<number>('/admin/report-notifications/unread-count')
    },

    /** Mark a single report as viewed */
    async markAsViewed(id: number): Promise<void> {
        await apiClient.put<void>(`/admin/report-notifications/${id}/viewed`)
    },

    /** Mark all reports as viewed */
    async markAllAsViewed(): Promise<void> {
        await apiClient.put<void>('/admin/report-notifications/viewed-all')
    },
}
