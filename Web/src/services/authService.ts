import apiClient from './apiClient'
import { LoginRequest, LoginResponse, User } from '../types'

export const authService = {
  async login(credentials: LoginRequest): Promise<{
    user: Partial<User>
    token: string
    refreshToken: string
  }> {
    try {
      const response = await apiClient.postRAW<LoginResponse>(
        '/auth/login',
        credentials
      )

      if (response.code === 1000 && response.result) {
        if (response.result.role !== 1) {
          throw new Error('Tai khoan khong co quyen admin.')
        }

        return {
          user: {
            id: response.result.id,
            email: response.result.email,
            fullName: response.result.fullName,
            role: response.result.role as 0 | 1,
            reputationScore: response.result.reputationScore,
          },
          token: response.result.token,
          refreshToken: response.result.refreshToken,
        }
      }

      throw new Error(response.message || 'Login failed')
    } catch (error: any) {
      if (error.code === 'ERR_NETWORK') {
        throw 'Khong the ket noi den server. Vui long kiem tra backend dang chay.'
      }
      throw error.response?.data?.message || error.message || 'Login failed'
    }
  },

  async getProfile(): Promise<User> {
    const user = localStorage.getItem('user')
    if (user) {
      return JSON.parse(user)
    }
    throw new Error('No user found')
  },

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    try {
      await apiClient.put<void>('/users/me/password', {
        currentPassword,
        newPassword,
      })
    } catch (error: any) {
      throw error.response?.data?.message || error.message || 'Failed to change password'
    }
  },

  logout(): void {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')
  },
}
