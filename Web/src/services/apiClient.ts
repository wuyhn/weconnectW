import axios, { AxiosInstance, AxiosError, AxiosResponse } from 'axios'
import { ApiResponse } from '../types'

// Use relative URL in dev (proxy will handle it), or full URL from env
const API_BASE_URL = import.meta.env.VITE_API_URL || '/api'

class ApiClient {
  private client: AxiosInstance
  private refreshPromise: Promise<string | null> | null = null

  constructor() {
    this.client = axios.create({
      baseURL: API_BASE_URL,
      timeout: 15000,
      headers: {
        'Content-Type': 'application/json',
      },
    })

    // Request interceptor - add token to headers
    this.client.interceptors.request.use((config) => {
      const token = localStorage.getItem('token')
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
      }
      return config
    })

    // Response interceptor - refresh access token once before logging out
    this.client.interceptors.response.use(
      (response: AxiosResponse) => response,
      async (error: AxiosError) => {
        const originalRequest = error.config as any
        const requestUrl = originalRequest?.url || ''

        if (
          error.response?.status === 401 &&
          originalRequest &&
          !originalRequest._retry &&
          !requestUrl.includes('/auth/login')
        ) {
          originalRequest._retry = true
          const newToken = await this.refreshAccessToken()

          if (newToken) {
            originalRequest.headers = originalRequest.headers || {}
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            return this.client(originalRequest)
          }

          this.clearAuthAndRedirect()
        }

        return Promise.reject(error)
      }
    )
  }

  private clearAuthAndRedirect(): void {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')

    if (window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
  }

  private async refreshAccessToken(): Promise<string | null> {
    if (this.refreshPromise) {
      return this.refreshPromise
    }

    const refreshToken = localStorage.getItem('refreshToken')
    if (!refreshToken) {
      return null
    }

    this.refreshPromise = axios
      .post<ApiResponse<{ token: string; refreshToken: string }>>(
        `${API_BASE_URL}/auth/refresh`,
        { refreshToken },
        { headers: { 'Content-Type': 'application/json' } }
      )
      .then((response) => {
        const result = response.data.result
        if (!result?.token) {
          return null
        }

        localStorage.setItem('token', result.token)
        if (result.refreshToken) {
          localStorage.setItem('refreshToken', result.refreshToken)
        }

        return result.token
      })
      .catch(() => null)
      .finally(() => {
        this.refreshPromise = null
      })

    return this.refreshPromise
  }

  async get<T>(url: string, config?: any): Promise<T> {
    const response = await this.client.get<ApiResponse<T>>(url, config)
    return response.data.result
  }

  async post<T>(url: string, data?: any, config?: any): Promise<T> {
    const response = await this.client.post<ApiResponse<T>>(url, data, config)
    return response.data.result
  }

  async put<T>(url: string, data?: any, config?: any): Promise<T> {
    const response = await this.client.put<ApiResponse<T>>(url, data, config)
    return response.data.result
  }

  async delete<T>(url: string, config?: any): Promise<T> {
    const response = await this.client.delete<ApiResponse<T>>(url, config)
    return response.data.result
  }

  // For endpoints that don't follow ApiResponse pattern
  async getRAW<T>(url: string, config?: any): Promise<T> {
    const response = await this.client.get<T>(url, config)
    return response.data
  }

  async postRAW<T>(url: string, data?: any, config?: any): Promise<T> {
    const response = await this.client.post<T>(url, data, config)
    return response.data
  }
}

export default new ApiClient()
