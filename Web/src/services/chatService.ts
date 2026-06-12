import apiClient from './apiClient'

export interface ChatRoom {
  id: number
  postId?: number
  title: string
  type: string
  lastMessagePreview: string
  lastMessageTime: string
  unreadCount: number
  otherUserAvatarUrl?: string
  memberCount: number
}

export const chatService = {
  async getTotalUnreadCount(): Promise<number> {
    try {
      const result = await apiClient.get<{ total: number }>('/chat/unread-total')
      return result?.total ?? 0
    } catch {
      return 0
    }
  },

  async getRooms(): Promise<ChatRoom[]> {
    try {
      return await apiClient.get<ChatRoom[]>('/chat/rooms')
    } catch {
      return []
    }
  },
}
