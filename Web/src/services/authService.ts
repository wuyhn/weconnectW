import {
  signInWithEmailAndPassword,
  signOut,
  EmailAuthProvider,
  reauthenticateWithCredential,
  updatePassword,
} from 'firebase/auth'
import { doc, getDoc, updateDoc } from 'firebase/firestore'
import { auth, db } from '../lib/firebase'
import { User } from '../types'

export const authService = {
  /**
   * Login with Firebase Auth
   * Kiểm tra role admin trong Firestore users/{uid}
   */
  async login(credentials: { email: string; password: string }): Promise<{
    user: Partial<User>
    token: string
  }> {
    try {
      const userCredential = await signInWithEmailAndPassword(
        auth,
        credentials.email,
        credentials.password
      )
      const firebaseUser = userCredential.user

      // Kiểm tra profile trong Firestore
      const userDoc = await getDoc(doc(db, 'users', firebaseUser.uid))
      if (!userDoc.exists()) {
        await signOut(auth)
        throw new Error('Tài khoản không tồn tại trong hệ thống.')
      }

      const data = userDoc.data()

      // Chỉ admin (role = 1 hoặc role = "admin") mới được vào
      if (data.role !== 1 && data.role !== 'admin') {
        await signOut(auth)
        throw new Error('Tài khoản không có quyền admin. Chỉ tài khoản admin mới được truy cập.')
      }

      const token = await firebaseUser.getIdToken()

      return {
        user: {
          id: firebaseUser.uid as any,
          email: firebaseUser.email || '',
          fullName: data.fullName || firebaseUser.displayName || '',
          role: 1,
          isBlocked: false,
          createdAt: data.createdAt || '',
        },
        token,
      }
    } catch (error: any) {
      const code = error.code
      if (code === 'auth/user-not-found' || code === 'auth/wrong-password' || code === 'auth/invalid-credential') {
        throw 'Email hoặc mật khẩu không đúng.'
      }
      if (code === 'auth/too-many-requests') {
        throw 'Đăng nhập thất bại quá nhiều lần. Vui lòng thử lại sau.'
      }
      throw error.message || 'Đăng nhập thất bại.'
    }
  },

  /**
   * Get current admin user info from localStorage
   */
  async getProfile(): Promise<User> {
    const user = localStorage.getItem('user')
    if (user) return JSON.parse(user)
    throw new Error('No user found')
  },

  /**
   * Change password (requires re-authentication)
   */
  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    const user = auth.currentUser
    if (!user || !user.email) throw new Error('Không có người dùng đang đăng nhập.')

    try {
      // Re-authenticate trước khi đổi mật khẩu (Firebase yêu cầu)
      const credential = EmailAuthProvider.credential(user.email, currentPassword)
      await reauthenticateWithCredential(user, credential)
      await updatePassword(user, newPassword)
    } catch (error: any) {
      const code = error.code
      if (code === 'auth/wrong-password' || code === 'auth/invalid-credential') {
        throw 'Mật khẩu hiện tại không đúng.'
      }
      throw error.message || 'Không thể đổi mật khẩu.'
    }
  },

  /**
   * Logout - clear Firebase session
   */
  async logout(): Promise<void> {
    await signOut(auth)
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  },
}
