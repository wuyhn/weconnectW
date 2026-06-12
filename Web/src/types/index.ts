// User stats (from /admin/users/:id/stats)
export interface UserStats {
  totalPostsCreated: number
  totalActivitiesJoined: number
  totalReviewsReceived: number
  totalReportsReceived: number
  confirmedViolations: number
}

// User types
export interface User {
  id: number
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
    id: number
    email: string
    fullName: string
    token: string
    refreshToken: string
    message: string
    role: number
    reputationScore?: number
  }
}

export interface ApiResponse<T> {
  code: number
  message: string
  result: T
}

// Post types
export interface Post {
  id: number
  authorId: number
  authorName?: string
  authorAvatarUrl?: string
  content: string
  interestTag: string
  location: string
  imageUrl?: string
  maxMembers: number
  memberCount?: number
  likesCount?: number
  commentsCount?: number
  startTime: string
  endTime: string
  activityEndTime?: string
  archived: boolean
  cancelled?: boolean
  expired?: boolean
  expirationHours?: number
  activityTimeType?: string
  createdAt: string
}

// Review types
export interface UserReview {
  id: number
  reviewerId: number
  reviewedUserId: number
  postId?: number
  reviewerName?: string
  reviewerAvatarUrl?: string
  reviewedUserName?: string
  reviewedUserAvatarUrl?: string
  activityName: string
  interestTag?: string
  activityStartTime?: string  // "DD/MM/YYYY" pre-formatted from backend
  activityEndTime?: string
  activityDateDisplay?: string
  rating?: number
  reputationLabel: string
  comment: string
  createdAt: string
  updatedAt?: string
  isEdited?: boolean
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
  id: number
  reporterId: number
  reporterName?: string
  reporterAvatarUrl?: string
  targetType: 'USER' | 'POST' | 'REVIEW'
  targetId: number
  targetName?: string
  targetAvatarUrl?: string
  targetThumbnailUrl?: string
  reason: string
  description?: string
  status: 'PENDING' | 'VALID' | 'REJECTED'
  penaltyPoint?: number
  suggestedPenalty?: number
  penaltyOptions?: number[]
  adminAction?: string
  createdAt: string
  reviewedAt?: string
  reviewedBy?: number
  evidenceImages?: string[]
  targetInfo?: PostTargetInfo | UserTargetInfo
}

// Dashboard types
export interface TopInterestTag {
  tag: string
  count: number
}

export interface DashboardStats {
  totalUsers: number
  totalPosts: number
  activePosts?: number
  totalReviews: number
  blockedUsers: number
  archivedPosts: number
  topInterestTags?: TopInterestTag[]
}

export interface TrendPoint {
  date: string  // "DD/MM" format from backend
  users: number
  posts: number
  reviews: number
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
  targetType?: 'USER' | 'POST' | 'REVIEW' | null
  status?: 'PENDING' | 'VALID' | 'REJECTED' | null
  statusGroup?: 'OPEN' | 'CLOSED' | null
}
