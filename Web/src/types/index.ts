// User types
export interface User {
  id: string
  email: string
  password?: string
  fullName: string
  birthday?: string
  gender?: string
  avatarUrl?: string
  bio?: string
  interestTags?: string[]
  averageRating?: number
  reputationScore?: number
  isBlocked: boolean
  role: 0 | 1  // 0 = user, 1 = admin
  createdAt: string
  postCount?: number  // Total posts created by this user
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  code: number
  message: string
  result: {
    id: string
    email: string
    fullName: string
    token: string
    message: string
    role: number
  }
}

export interface ApiResponse<T> {
  code: number
  message: string
  result: T
}

// Post types
export interface Post {
  id: string
  authorId: string
  authorName?: string  // Author's full name for display
  content: string
  interestTag: string
  location: string
  imageUrl?: string
  maxMembers: number
  startTime: string
  endTime: string
  archived: boolean
  createdAt: string
}

// Review types
export interface UserReview {
  id: string
  reviewerId: string
  reviewedUserId: string
  reviewerName?: string
  reviewedUserName?: string
  activityName: string
  reputationLabel: string
  comment: string
  createdAt: string
}

// Report target info types
export interface PostTargetInfo {
  content: string
  interestTag?: string
  location?: string
  imageUrl?: string
  authorId: number
  authorName?: string
  maxMembers?: number
  startTime?: string
  endTime?: string
  archived: boolean
  createdAt?: string
}

export interface UserTargetInfo {
  fullName: string
  email?: string
  bio?: string
  interestTags?: string[] | string
  avatarUrl?: string
  averageRating?: number
  reputationScore?: number
  isBlocked: boolean
  gender?: string
  createdAt?: string
}

// Report types
export interface Report {
  id: string
  reporterId: string
  reporterName?: string
  targetType: 'USER' | 'POST'
  targetId: string
  reason: string
  description?: string
  status: 'PENDING' | 'REVIEWED' | 'RESOLVED'
  adminAction?: string
  createdAt: string
  reviewedAt?: string
  reviewedBy?: string
  targetInfo?: PostTargetInfo | UserTargetInfo
}

// Dashboard types
export interface DashboardStats {
  totalUsers: number
  totalPosts: number
  totalReviews: number
  blockedUsers: number
  archivedPosts: number
}

// Pagination types
export interface PaginationParams {
  page: number
  pageSize: number
}

export interface PaginatedResponse<T> {
  data: T[]
  total: number
  page: number
  pageSize: number
}

// Filter types
export interface UserFilter {
  search?: string
  role?: 0 | 1 | null
  isBlocked?: boolean | null
}

export interface PostFilter {
  search?: string
  archived?: boolean | null
}

export interface ReviewFilter {
  search?: string
  reputationLabel?: string | null
}

export interface ReportFilter {
  search?: string
  targetType?: 'USER' | 'POST' | null
  status?: 'PENDING' | 'REVIEWED' | 'RESOLVED' | null
}
