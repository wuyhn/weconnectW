import { create } from 'zustand'
import { signOut } from 'firebase/auth'
import { auth } from '../lib/firebase'
import { User } from '../types'

interface AuthStore {
  user: User | null
  token: string | null
  loading: boolean
  error: string | null
  login: (user: User, token: string) => void
  logout: () => void
  setError: (error: string | null) => void
  setLoading: (loading: boolean) => void
}

export const useAuthStore = create<AuthStore>((set) => ({
  user: localStorage.getItem('user') ? JSON.parse(localStorage.getItem('user')!) : null,
  token: localStorage.getItem('token'),
  loading: false,
  error: null,

  login: (user, token) => {
    localStorage.setItem('user', JSON.stringify(user))
    localStorage.setItem('token', token)
    set({ user, token, error: null })
  },

  logout: () => {
    // Sign out from Firebase
    signOut(auth).catch(() => {})
    localStorage.removeItem('user')
    localStorage.removeItem('token')
    set({ user: null, token: null, error: null })
  },

  setError: (error) => set({ error }),
  setLoading: (loading) => set({ loading }),
}))
