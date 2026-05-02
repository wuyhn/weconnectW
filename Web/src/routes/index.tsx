import { Routes, Route, Navigate } from 'react-router-dom'
import LoginPage from '../pages/LoginPage'
import DashboardPage from '../pages/DashboardPage'
import UsersPage from '../pages/UsersPage'
import PostsPage from '../pages/PostsPage'
import ReviewsPage from '../pages/ReviewsPage'
import ReportsPage from '../pages/ReportsPage'
import AdminUserDetailPage from '../pages/AdminUserDetailPage'
import AdminPostDetailPage from '../pages/AdminPostDetailPage'
import AdminReviewDetailPage from '../pages/AdminReviewDetailPage'
import SearchResultsPage from '../pages/SearchResultsPage'

interface AppRoutesProps {
  isAuthenticated: boolean
}

export default function AppRoutes({ isAuthenticated }: AppRoutesProps) {
  return (
    <Routes>
      {/* Public routes */}
      <Route
        path="/login"
        element={isAuthenticated ? <Navigate to="/dashboard" /> : <LoginPage />}
      />

      {/* Protected routes */}
      {isAuthenticated ? (
        <>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/users" element={<UsersPage />} />
          <Route path="/admin/users/:id" element={<AdminUserDetailPage />} />
          <Route path="/posts" element={<PostsPage />} />
          <Route path="/admin/posts/:id" element={<AdminPostDetailPage />} />
          <Route path="/reviews" element={<ReviewsPage />} />
          <Route path="/admin/reviews/:id" element={<AdminReviewDetailPage />} />
          <Route path="/reports" element={<ReportsPage />} />
          <Route path="/search" element={<SearchResultsPage />} />
        </>
      ) : (
        <>
          <Route path="*" element={<Navigate to="/login" />} />
        </>
      )}

      {/* Default redirect */}
      <Route
        path="/"
        element={isAuthenticated ? <Navigate to="/dashboard" /> : <Navigate to="/login" />}
      />
    </Routes>
  )
}
