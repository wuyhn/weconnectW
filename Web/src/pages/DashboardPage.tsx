import { useEffect, useMemo, useState } from 'react'
import type { CSSProperties, ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Empty, message } from 'antd'
import {
  BarChartOutlined,
  EyeOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  LineChartOutlined,
  LockOutlined,
  StarOutlined,
  TagsOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import MainLayout from '../components/MainLayout'
import LoadingState from '../components/LoadingState'
import { dashboardService } from '../services/dashboardService'
import { userAdminService } from '../services/userAdminService'
import { postAdminService } from '../services/postAdminService'
import { reviewAdminService } from '../services/reviewAdminService'
import { DashboardStats, Post, TrendPoint, User, UserReview } from '../types'
import { resolveAvatarUrl } from '../utils/avatar'
import { cleanTagText } from '../utils/text'
import './DashboardPage.css'

type StatTone = 'pink' | 'green' | 'orange' | 'purple' | 'blue'

interface StatCardItem {
  title: string
  value: number
  detail: string
  icon: ReactNode
  tone: StatTone
  route: string
}

interface ActivityPoint {
  label: string
  users: number
  posts: number
  reviews: number
}

const statToneVars: Record<StatTone, CSSProperties> = {
  pink: {
    '--card-bg': '#fff6f9',
    '--accent': '#ee4d7d',
    '--icon-bg': '#ffe2eb',
  } as CSSProperties,
  green: {
    '--card-bg': '#f6fff9',
    '--accent': '#20b968',
    '--icon-bg': '#dcf8e8',
  } as CSSProperties,
  orange: {
    '--card-bg': '#fffaf0',
    '--accent': '#f29d21',
    '--icon-bg': '#fff0cf',
  } as CSSProperties,
  purple: {
    '--card-bg': '#fbf8ff',
    '--accent': '#8b5bd6',
    '--icon-bg': '#ece0ff',
  } as CSSProperties,
  blue: {
    '--card-bg': '#f3fbff',
    '--accent': '#1696b7',
    '--icon-bg': '#d9f4fb',
  } as CSSProperties,
}

const normalizeDate = (value: unknown): dayjs.Dayjs | null => {
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value as number[]
    if (!year || !month || !day) return null
    const parsed = dayjs(new Date(year, month - 1, day, hour, minute, second))
    return parsed.isValid() ? parsed : null
  }

  if (!value) return null

  // Handle "DD/MM/YYYY" pre-formatted strings from backend (e.g. reviews)
  if (typeof value === 'string' && /^\d{2}\/\d{2}\/\d{4}$/.test(value)) {
    const [day, month, year] = value.split('/').map(Number)
    const parsed = dayjs(new Date(year, month - 1, day))
    return parsed.isValid() ? parsed : null
  }

  const parsed = dayjs(value as string | number | Date)
  return parsed.isValid() ? parsed : null
}

const formatDate = (value: unknown): string => {
  const parsed = normalizeDate(value)
  return parsed ? parsed.format('DD/MM/YYYY') : '—'
}

const getPostStatus = (post: Post): { label: string; className: string } => {
  if (post.cancelled) return { label: 'Đã hủy', className: 'dashboard-status-cancelled' }
  const endDate = normalizeDate(post.endTime)
  if (post.archived || Boolean(endDate?.isBefore(dayjs()))) return { label: 'Lưu trữ', className: 'dashboard-status-archived' }
  return { label: 'Đang hoạt động', className: 'dashboard-status-active' }
}

const reputationTone = (score?: number) => {
  if (typeof score !== 'number') return 'medium'
  if (score >= 80) return 'high'
  if (score >= 50) return 'medium'
  return 'low'
}

const buildActivityDataFromTrends = (trends: TrendPoint[]): ActivityPoint[] =>
  trends.map((t) => ({
    label: t.date,
    users: t.users,
    posts: t.posts,
    reviews: t.reviews,
  }))

const UserAvatar = ({ name, avatarUrl }: { name?: string; avatarUrl?: string | null }) => {
  const [imgError, setImgError] = useState(false)
  const resolved = resolveAvatarUrl(avatarUrl)

  if (resolved && !imgError) {
    return (
      <img
        src={resolved}
        alt={name || ''}
        className="dashboard-user-avatar dashboard-user-avatar-img"
        onError={() => setImgError(true)}
      />
    )
  }
  return (
    <span className="dashboard-user-avatar dashboard-user-avatar-placeholder" aria-label={name || 'Người dùng'}>
      <UserOutlined />
    </span>
  )
}

const ActivityInfoCell = ({ review }: { review: UserReview }) => (
  <div className="activity-cell">
    {review.interestTag ? (
      <span className="tag-chip">{cleanTagText(review.interestTag)}</span>
    ) : (
      <span className="cell-primary ellipsis">{cleanTagText(review.activityName) || '—'}</span>
    )}
    <span className="cell-muted activity-date">{review.activityStartTime || '—'}</span>
  </div>
)

const DashboardSection = ({
  icon,
  title,
  onViewAll,
  action,
  children,
}: {
  icon: ReactNode
  title: string
  onViewAll?: () => void
  action?: ReactNode
  children: ReactNode
}) => (
  <section className="dashboard-card">
    <div className="dashboard-card-header">
      <div className="dashboard-card-title">
        {icon}
        <span>{title}</span>
      </div>
      {action ||
        (onViewAll && (
          <Button type="text" size="small" className="view-all-button" onClick={onViewAll} icon={<EyeOutlined />}>
            Xem tất cả
          </Button>
        ))}
    </div>
    <div className="dashboard-card-body">{children}</div>
  </section>
)

const EmptyTable = ({ description }: { description: string }) => (
  <div className="table-empty">
    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={description} />
  </div>
)

const ActivityLineChart = ({ data }: { data: ActivityPoint[] }) => {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null)
  const width = 560
  const height = 190
  const padding = { top: 22, right: 18, bottom: 34, left: 38 }
  const maxValue = Math.max(1, ...data.flatMap((item) => [item.users, item.posts, item.reviews]))
  const chartWidth = width - padding.left - padding.right
  const chartHeight = height - padding.top - padding.bottom

  const getPoint = (value: number, index: number) => {
    const x = padding.left + (chartWidth / Math.max(1, data.length - 1)) * index
    const y = padding.top + chartHeight - (value / maxValue) * chartHeight
    return { x, y }
  }

  const getPath = (key: keyof ActivityPoint) =>
    data
      .map((item, index) => {
        const point = getPoint(Number(item[key]), index)
        return `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(2)} ${point.y.toFixed(2)}`
      })
      .join(' ')

  const latestPoint = data[data.length - 1]
  const series = [
    { key: 'users' as const, label: 'Người dùng mới', color: '#3a9cf7', value: latestPoint?.users ?? 0 },
    { key: 'posts' as const, label: 'Bài viết mới', color: '#65c85d', value: latestPoint?.posts ?? 0 },
    { key: 'reviews' as const, label: 'Đánh giá mới', color: '#ffb629', value: latestPoint?.reviews ?? 0 },
  ]

  const hoveredPoint = hoveredIndex !== null ? data[hoveredIndex] : null
  const hoveredX = hoveredIndex !== null
    ? padding.left + (chartWidth / Math.max(1, data.length - 1)) * hoveredIndex
    : null
  const tooltipLeftPct = hoveredX !== null ? (hoveredX / width) * 100 : 0
  const tooltipTransform =
    hoveredIndex !== null && hoveredIndex >= data.length - 2
      ? 'translateX(-100%)'
      : hoveredIndex !== null && hoveredIndex <= 1
      ? 'translateX(0%)'
      : 'translateX(-50%)'

  return (
    <div className="activity-chart">
      <div className="activity-chart-legend">
        {series.map((item) => (
          <div className="chart-legend-item" key={item.key}>
            <span className="legend-dot" style={{ background: item.color }} />
            <span>{item.label}</span>
          </div>
        ))}
      </div>
      <div className="chart-svg-wrapper">
        {hoveredPoint && hoveredX !== null && (
          <div className="chart-tooltip" style={{ left: `${tooltipLeftPct}%`, transform: tooltipTransform }}>
            <div className="chart-tooltip-date">{hoveredPoint.label}</div>
            {series.map((s) => (
              <div key={s.key} className="chart-tooltip-row">
                <span className="chart-tooltip-dot" style={{ background: s.color }} />
                <span className="chart-tooltip-label">{s.label}</span>
                <span className="chart-tooltip-value">{hoveredPoint[s.key]}</span>
              </div>
            ))}
          </div>
        )}
        <svg className="line-chart-svg" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Hoạt động 7 ngày qua">
          {[0, 0.5, 1].map((line) => {
            const y = padding.top + chartHeight - chartHeight * line
            return (
              <g key={line}>
                <line x1={padding.left} x2={width - padding.right} y1={y} y2={y} className="chart-grid-line" />
                <text x={padding.left - 12} y={y + 4} className="chart-axis-label" textAnchor="end">
                  {Math.round(maxValue * line)}
                </text>
              </g>
            )
          })}

          {hoveredX !== null && (
            <line
              x1={hoveredX} x2={hoveredX}
              y1={padding.top} y2={padding.top + chartHeight}
              className="chart-hover-line"
            />
          )}

          {series.map((item) => (
            <g key={item.key}>
              <path d={getPath(item.key)} fill="none" stroke={item.color} strokeWidth="3" strokeLinecap="round" />
              {data.map((point, index) => {
                const coordinate = getPoint(Number(point[item.key]), index)
                const isHovered = hoveredIndex === index
                return (
                  <circle
                    key={`${item.key}-${point.label}`}
                    cx={coordinate.x}
                    cy={coordinate.y}
                    r={isHovered ? 6 : 4}
                    fill={isHovered ? item.color : '#fff'}
                    stroke={item.color}
                    strokeWidth="2"
                  />
                )
              })}
            </g>
          ))}

          {data.map((item, index) => {
            const x = padding.left + (chartWidth / Math.max(1, data.length - 1)) * index
            return (
              <text key={item.label} x={x} y={height - 10} className="chart-axis-label" textAnchor="middle">
                {item.label}
              </text>
            )
          })}

          {data.map((_, index) => {
            const x = padding.left + (chartWidth / Math.max(1, data.length - 1)) * index
            const zoneW = chartWidth / Math.max(1, data.length - 1)
            return (
              <rect
                key={index}
                x={x - zoneW / 2}
                y={padding.top}
                width={zoneW}
                height={chartHeight}
                fill="transparent"
                style={{ cursor: 'crosshair' }}
                onMouseEnter={() => setHoveredIndex(index)}
                onMouseLeave={() => setHoveredIndex(null)}
              />
            )
          })}
        </svg>
      </div>
    </div>
  )
}

export default function DashboardPage() {
  const navigate = useNavigate()
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [users, setUsers] = useState<User[]>([])
  const [posts, setPosts] = useState<Post[]>([])
  const [reviews, setReviews] = useState<UserReview[]>([])
  const [trends, setTrends] = useState<TrendPoint[]>([])
  const [authorMap, setAuthorMap] = useState<Record<number, string>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [retryCount, setRetryCount] = useState(0)

  useEffect(() => {
    const loadData = async () => {
      try {
        setLoading(true)
        setError(null)
        const [statsData, usersData, postsData, reviewsData, trendsData, allUsers] = await Promise.all([
          dashboardService.getStats(),
          userAdminService.getRecentUsers(5),
          postAdminService.getRecentPosts(5),
          reviewAdminService.getRecentReviews(5),
          dashboardService.getTrends(7),
          userAdminService.getAllUsersRaw(),
        ])

        setStats(statsData)
        setUsers(usersData)
        setPosts(postsData)
        setReviews(reviewsData)
        setTrends(trendsData)
        setAuthorMap(
          Object.fromEntries(allUsers.map((u) => [u.id, u.fullName || u.email || `User ${u.id}`]))
        )
      } catch {
        setError('Không thể tải dữ liệu bảng điều khiển')
        message.error('Không thể tải dữ liệu bảng điều khiển')
      } finally {
        setLoading(false)
      }
    }

    loadData()
  }, [retryCount])

  const statCards: StatCardItem[] = useMemo(
    () => [
      {
        title: 'Tổng người dùng',
        value: stats?.totalUsers ?? 0,
        detail: `${stats?.totalUsers ?? 0} người dùng đã đăng ký`,
        icon: <TeamOutlined />,
        tone: 'pink',
        route: '/users',
      },
      {
        title: 'Tổng bài viết',
        value: stats?.totalPosts ?? 0,
        detail: `${stats?.activePosts ?? 0} đang hoạt động`,
        icon: <FileTextOutlined />,
        tone: 'green',
        route: '/posts',
      },
      {
        title: 'Tổng đánh giá',
        value: stats?.totalReviews ?? 0,
        detail: `${stats?.totalReviews ?? 0} đánh giá từ người dùng`,
        icon: <StarOutlined />,
        tone: 'orange',
        route: '/reviews',
      },
      {
        title: 'Người dùng bị khóa',
        value: stats?.blockedUsers ?? 0,
        detail: `${stats?.blockedUsers ?? 0} tài khoản bị hạn chế`,
        icon: <LockOutlined />,
        tone: 'purple',
        route: '/users?blocked=true',
      },
      {
        title: 'Bài viết đã lưu trữ',
        value: stats?.archivedPosts ?? 0,
        detail: `${stats?.archivedPosts ?? 0} bài viết không hoạt động`,
        icon: <FolderOpenOutlined />,
        tone: 'blue',
        route: '/posts?status=archived',
      },
    ],
    [stats]
  )

  const activityData = useMemo(() => buildActivityDataFromTrends(trends), [trends])
  const topInterestTags = useMemo(() => stats?.topInterestTags?.slice(0, 5) ?? [], [stats])
  const topInterestTagMax = useMemo(
    () => Math.max(1, ...topInterestTags.map((item) => item.count)),
    [topInterestTags]
  )

  if (loading) {
    return <LoadingState fullPage message="Đang tải bảng điều khiển..." />
  }

  if (error) {
    return (
      <MainLayout>
        <div className="dashboard-error-state">
          <p>{error}</p>
          <Button type="primary" onClick={() => setRetryCount((c) => c + 1)}>
            Thử lại
          </Button>
        </div>
      </MainLayout>
    )
  }

  return (
    <MainLayout>
      <div className="dashboard-page">
        <section className="stats-grid" aria-label="Chỉ số tổng quan">
          {statCards.map((card) => (
            // Điều hướng nhanh: mỗi card chỉ số hoạt động như một nút để tránh điểm chết UX.
            <article
              className="metric-card metric-card-clickable"
              style={statToneVars[card.tone]}
              key={card.title}
              role="button"
              tabIndex={0}
              aria-label={`Mở trang ${card.title}`}
              onClick={() => navigate(card.route)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault()
                  navigate(card.route)
                }
              }}
            >
              <div className="metric-icon">{card.icon}</div>
              <div className="metric-content">
                <span className="metric-label">{card.title}</span>
                <strong className="metric-value">{card.value.toLocaleString('vi-VN')}</strong>
                <span className="metric-detail">{card.detail}</span>
              </div>
            </article>
          ))}
        </section>

        <section className="dashboard-data-grid">
          <DashboardSection
            icon={<UserOutlined />}
            title="Người dùng mới nhất"
            onViewAll={() => navigate('/users?from=dashboard&sort=createdAt_desc')}
          >
            {users.length === 0 ? (
              <EmptyTable description="Chưa có người dùng mới" />
            ) : (
              <div className="data-table-wrapper">
                <div className="data-table users-table">
                  <div className="data-table-row data-table-head">
                    <span>Người dùng</span>
                    <span className="hide-sm">Email</span>
                    <span>Đánh giá</span>
                    <span>Uy tín</span>
                    <span className="hide-md">Ngày tham gia</span>
                  </div>
                  {users.map((user) => (
                    <div className="data-table-row" key={user.id}>
                      <div className="user-cell">
                        <UserAvatar name={user.fullName} avatarUrl={user.avatarUrl} />
                        <span className="cell-primary">{user.fullName || 'Người dùng'}</span>
                      </div>
                      <span className="cell-muted ellipsis hide-sm" title={user.email}>
                        {user.email}
                      </span>
                      <span className="rating-cell">
                        {(user.averageRating ?? 0).toFixed(1)}
                      </span>
                      <span className={`soft-badge reputation-${reputationTone(user.reputationScore)}`}>
                        {user.reputationScore != null ? Math.round(user.reputationScore) : 100}
                      </span>
                      <span className="cell-muted hide-md">{formatDate(user.createdAt)}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </DashboardSection>

          <DashboardSection
            icon={<FileTextOutlined />}
            title="Bài viết mới nhất"
            onViewAll={() => navigate('/posts?from=dashboard&sort=createdAt_desc')}
          >
            {posts.length === 0 ? (
              <EmptyTable description="Chưa có bài viết mới" />
            ) : (
              <div className="data-table-wrapper">
                <div className="data-table posts-table">
                  <div className="data-table-row data-table-head">
                    <span>Tiêu đề</span>
                    <span>Tác giả / Tag</span>
                    <span className="hide-md">Slots</span>
                    <span>Trạng thái</span>
                    <span className="hide-sm">Ngày tạo</span>
                  </div>
                  {posts.map((post) => {
                    const postStatus = getPostStatus(post)
                    return (
                      <div className="data-table-row" key={post.id}>
                        <span className="cell-primary ellipsis" title={post.content}>
                          {post.content || 'Bài viết'}
                        </span>
                        <div className="author-tag-cell">
                          <span className="ellipsis">
                            {post.authorName || authorMap[post.authorId] || `User ${post.authorId}`}
                          </span>
                          <span className="tag-chip">{cleanTagText(post.interestTag)}</span>
                        </div>
                        <span className="cell-muted hide-md">{post.maxMembers || 0}</span>
                        <span className={`status-pill ${postStatus.className}`}>
                          <span className="status-dot" aria-hidden="true" />
                          {postStatus.label}
                        </span>
                        <span className="cell-muted hide-sm">{formatDate(post.createdAt)}</span>
                      </div>
                    )
                  })}
                </div>
              </div>
            )}
          </DashboardSection>

          <DashboardSection
            icon={<TagsOutlined />}
            title="Interest tag phổ biến"
            action={<span className="dashboard-card-note">Top 5 theo người dùng</span>}
          >
            {topInterestTags.length === 0 ? (
              <EmptyTable description="Chưa có dữ liệu interest tag" />
            ) : (
              <div className="top-tags-list">
                {topInterestTags.map((item, index) => (
                  <div className="top-tag-row" key={item.tag}>
                    <div className="top-tag-rank">{index + 1}</div>
                    <div className="top-tag-main">
                      <div className="top-tag-meta">
                        <span className="top-tag-name" title={cleanTagText(item.tag)}>
                          {cleanTagText(item.tag)}
                        </span>
                        <strong>{item.count.toLocaleString('vi-VN')} người dùng</strong>
                      </div>
                      <div className="top-tag-track" aria-hidden="true">
                        <span style={{ width: `${Math.max(8, (item.count / topInterestTagMax) * 100)}%` }} />
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </DashboardSection>

          <DashboardSection
            icon={<StarOutlined />}
            title="Đánh giá mới nhất"
            onViewAll={() => navigate('/reviews?from=dashboard&sort=createdAt_desc')}
          >
            {reviews.length === 0 ? (
              <EmptyTable description="Chưa có đánh giá mới" />
            ) : (
              <div className="data-table-wrapper">
                <div className="data-table reviews-table">
                  <div className="data-table-row data-table-head">
                    <span>Hoạt động</span>
                    <span>Người đánh giá</span>
                    <span className="hide-md">Người được đánh giá</span>
                    <span>Rating</span>
                    <span className="hide-sm">Ngày tạo</span>
                  </div>
                  {reviews.map((review) => (
                    <div className="data-table-row" key={review.id}>
                      <ActivityInfoCell review={review} />
                      <div className="user-cell">
                        <UserAvatar name={review.reviewerName} avatarUrl={review.reviewerAvatarUrl} />
                        <span className="cell-primary ellipsis">{review.reviewerName || '—'}</span>
                      </div>
                      <div className="user-cell hide-md">
                        <UserAvatar name={review.reviewedUserName} avatarUrl={review.reviewedUserAvatarUrl} />
                        <span className="cell-primary ellipsis">{review.reviewedUserName || '—'}</span>
                      </div>
                      <span className="rating-cell">
                        {review.rating != null ? review.rating.toFixed(1) : '—'}
                      </span>
                      <span className="cell-muted hide-sm">{formatDate(review.createdAt)}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </DashboardSection>

          <DashboardSection
            icon={<BarChartOutlined />}
            title="Tổng quan báo cáo"
            action={
              <Button size="small" className="period-button">
                7 ngày qua
              </Button>
            }
          >
            <div className="chart-panel-wrapper">
              <div className="chart-panel-title">
                <LineChartOutlined />
                <span>Hoạt động 7 ngày qua</span>
              </div>
              {activityData.length > 0 ? (
                <ActivityLineChart data={activityData} />
              ) : (
                <EmptyTable description="Chưa có dữ liệu hoạt động" />
              )}
            </div>
          </DashboardSection>
        </section>
      </div>
    </MainLayout>
  )
}
