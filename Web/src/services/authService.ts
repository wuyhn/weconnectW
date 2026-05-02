import apiClient from './apiClient'
import { LoginRequest, LoginResponse, User } from '../types'

// Note: Login endpoint is from real backend: POST /api/auth/login
// Other admin endpoints may be mocked initially

export const authService = {
  /**
   * Login with credentials
   * REAL API: POST /api/auth/login
   * Response format matches backend spec:
   * {
   *   "code": 1000,
   *   "message": "Đăng nhập thành công!",
   *   "result": {
   *     "id": number,
   *     "email": string,
   *     "fullName": string,
   *     "token": string,
   *     "message": string
   *   }
   * }
   */
  async login(credentials: LoginRequest): Promise<{
    user: Partial<User>
    token: string
  }> {
    try {
      // Always use real backend API
      const response = await apiClient.postRAW<LoginResponse>(
        '/auth/login',
        credentials
      )

      if (response.code === 1000 && response.result) {
        // TODO: Kiểm tra role - chỉ admin (role=1) mới được đăng nhập web
        // Tạm bỏ check role vì DB chưa có cột role
        // if (response.result.role !== 1) {
        //   throw new Error('Tài khoản không có quyền admin. Chỉ tài khoản admin mới được truy cập.')
        // }

        return {
          user: {
            id: response.result.id,
            email: response.result.email,
            fullName: response.result.fullName,
            role: (response.result.role || 1) as 0 | 1, // Mặc định role=1 cho admin
          },
          token: response.result.token,
        }
      }

      throw new Error(response.message || 'Login failed')
    } catch (error: any) {
      // If backend is unreachable, throw meaningful error
      if (error.code === 'ERR_NETWORK') {
        throw 'Không thể kết nối đến server. Vui lòng kiểm tra backend đang chạy.'
      }
      throw error.response?.data?.message || error.message || 'Login failed'
    }
  },

  /**
   * Get current admin user info
   * MOCK: User is set from login response
   * TODO: Create real endpoint GET /api/admin/profile
   */
  async getProfile(): Promise<User> {
    // Get from localStorage for now
    const user = localStorage.getItem('user')
    if (user) {
      return JSON.parse(user)
    }
    throw new Error('No user found')
  },

  /**
   * Change password
   * API: POST /api/auth/change-password
   */
  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    try {
      const response = await apiClient.postRAW<any>(
        '/auth/change-password',
        {
          currentPassword,
          newPassword,
        }
      )

      if (response?.code === 1000) {
        return
      }

      throw new Error(response?.message || 'Failed to change password')
    } catch (error: any) {
      throw error.response?.data?.message || error.message || 'Failed to change password'
    }
  },

  /**
   * Logout
   * Just clears local storage - no backend call needed
   */
  logout(): void {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  },
}
