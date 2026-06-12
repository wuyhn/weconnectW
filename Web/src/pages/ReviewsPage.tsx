import { useEffect, useState } from 'react'
import {
  Avatar,
  Button,
  Card,
  Drawer,
  Empty,
  Input,
  Select,
  Skeleton,
  Table,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  CalendarOutlined,
  CloseOutlined,
  EyeOutlined,
  MessageOutlined,
  ReloadOutlined,
  SearchOutlined,
  StarFilled,
  StarOutlined,
  TagsOutlined,
  UserOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import MainLayout from '../components/MainLayout'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { reviewAdminService, ReviewLabelFilter } from '../services/reviewAdminService'
import { PaginationParams, UserReview } from '../types'
import { resolveAvatarUrl } from '../utils/avatar'
import { cleanTagText } from '../utils/text'
import './ReviewsPage.css'

const PAGE_SIZE = 7

const normalizeDate = (value: unknown): dayjs.Dayjs | null => {
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value as number[]
    if (!year || !month || !day) return null
    const parsed = dayjs(new Date(year, month - 1, day, hour, minute, second))
    return parsed.isValid() ? parsed : null
  }

  if (!value) return null

  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) return null

    const viDate = trimmed.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})(?:\s*[·,-]?\s*(\d{1,2}):(\d{2}))?/)
    if (viDate) {
      const [, day, month, year, hour = '0', minute = '0'] = viDate
      const parsed = dayjs(new Date(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute)))
      return parsed.isValid() ? parsed : null
    }
  }

  const parsed = dayjs(value as string | number | Date)
  return parsed.isValid() ? parsed : null
}

const formatDate = (value: unknown) => {
  const parsed = normalizeDate(value)
  return parsed ? parsed.format('DD/MM/YYYY') : '—'
}

const formatActivityDate = (review: UserReview) => {
  const start = normalizeDate(review.activityStartTime)
  const end = normalizeDate(review.activityEndTime)

  if (start && end && !start.isSame(end, 'day')) {
    return `${start.format('DD/MM/YYYY')} - ${end.format('DD/MM/YYYY')}`
  }

  if (start) return start.format('DD/MM/YYYY')

  if (review.activityDateDisplay && !review.activityDateDisplay.toLowerCase().includes('invalid')) {
    return review.activityDateDisplay
  }

  return '—'
}

const clampRating = (value?: number) => {
  if (typeof value !== 'number' || Number.isNaN(value)) return 0
  return Math.max(0, Math.min(5, value))
}

const getRatingValue = (review: UserReview) => {
  const rating = clampRating(review.rating)
  if (rating > 0) return rating

  const label = (review.reputationLabel || '').toLowerCase()
  if (label.includes('excellent') || label.includes('đáng') || label.includes('dang')) return 5
  if (label.includes('good') || label.includes('nhiệt') || label.includes('nhiet') || label.includes('tích cực')) return 4
  if (label.includes('average') || label.includes('bình') || label.includes('binh') || label.includes('trung')) return 3
  if (label.includes('poor') || label.includes('không') || label.includes('khong') || label.includes('cải thiện')) return 2
  return 0
}

const getLabelMeta = (review: UserReview): { key: ReviewLabelFilter; text: string } => {
  const rating = getRatingValue(review)
  if (rating >= 4) return { key: 'positive', text: 'Tích cực' }
  if (rating === 3) return { key: 'medium', text: 'Trung bình' }
  if (rating > 0) return { key: 'improve', text: 'Cần cải thiện' }
  return { key: 'medium', text: 'Trung bình' }
}

const UserCell = ({
  name,
  id,
  avatarUrl,
}: {
  name?: string
  id: number
  avatarUrl?: string
}) => (
  <div className="reviews-user-cell">
    <Avatar
      size={38}
      src={resolveAvatarUrl(avatarUrl)}
      icon={!avatarUrl ? <UserOutlined /> : undefined}
      className="reviews-avatar"
    />
    <div className="reviews-user-meta">
      <span className="reviews-user-name" title={name || 'Người dùng'}>
        {name || 'Người dùng'}
      </span>
      <span className="reviews-user-id">ID #{id}</span>
    </div>
  </div>
)

const RatingStars = ({ rating }: { rating: number }) => {
  const rounded = Math.round(rating)
  return (
    <div className="reviews-rating">
      <strong>{rating ? rating.toFixed(1) : '—'}</strong>
      <div className="reviews-stars" aria-label={`${rating} sao`}>
        {[1, 2, 3, 4, 5].map((star) =>
          star <= rounded ? (
            <StarFilled className="star-filled" key={star} />
          ) : (
            <StarOutlined className="star-empty" key={star} />
          )
        )}
      </div>
    </div>
  )
}

const ReviewSkeletonRows = () => (
  <div className="reviews-skeleton">
    {Array.from({ length: PAGE_SIZE }).map((_, index) => (
      <div className="reviews-skeleton-row" key={index}>
        <Skeleton.Avatar active size={40} shape="circle" />
        <Skeleton.Input active size="small" block />
        <Skeleton.Input active size="small" block />
        <Skeleton.Button active size="small" />
      </div>
    ))}
  </div>
)

export default function ReviewsPage() {
  const [reviews, setReviews] = useState<UserReview[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [labelFilter, setLabelFilter] = useState<ReviewLabelFilter | 'all'>('all')
  const [ratingFilter, setRatingFilter] = useState<number | null>(null)
  const [pagination, setPagination] = useState({ current: 1, pageSize: PAGE_SIZE, total: 0 })
  const [selectedReview, setSelectedReview] = useState<UserReview | null>(null)
  const [drawerVisible, setDrawerVisible] = useState(false)
  const debouncedSearch = useDebouncedValue(search, 300)
  const searchPending = search !== debouncedSearch

  useEffect(() => {
    loadReviews(1, debouncedSearch)
  }, [debouncedSearch, labelFilter, ratingFilter])

  const resetFilters = () => {
    setSearch('')
    setLabelFilter('all')
    setRatingFilter(null)
  }

  const loadReviews = async (page: number, searchValue = debouncedSearch) => {
    try {
      setLoading(true)
      setError(null)
      const params: PaginationParams = { page, pageSize: PAGE_SIZE }
      const result = await reviewAdminService.getReviews(params, {
        search: searchValue,
        label: labelFilter === 'all' ? null : labelFilter,
        rating: ratingFilter,
      })

      setReviews(result.data)
      setPagination({ current: page, pageSize: PAGE_SIZE, total: result.total })
    } catch {
      const messageText = 'Không thể tải danh sách đánh giá'
      setError(messageText)
      message.error(messageText)
    } finally {
      setLoading(false)
    }
  }

  const refreshReviews = () => {
    loadReviews(1, search)
  }

  const columns: ColumnsType<UserReview> = [
    {
      title: 'Hoạt động',
      key: 'activity',
      width: 178,
      render: (_, record) => (
        <div className="reviews-activity-cell">
          <span className="reviews-activity-tag" title={cleanTagText(record.interestTag || record.activityName || 'Hoạt động')}>
            {cleanTagText(record.interestTag || record.activityName || 'Hoạt động')}
          </span>
          <span className="reviews-activity-date">
            <CalendarOutlined />
            {formatActivityDate(record)}
          </span>
        </div>
      ),
    },
    {
      title: 'Người đánh giá',
      key: 'reviewer',
      width: 210,
      render: (_, record) => (
        <UserCell name={record.reviewerName} id={record.reviewerId} avatarUrl={record.reviewerAvatarUrl} />
      ),
    },
    {
      title: 'Người được đánh giá',
      key: 'reviewedUser',
      width: 220,
      render: (_, record) => (
        <UserCell
          name={record.reviewedUserName}
          id={record.reviewedUserId}
          avatarUrl={record.reviewedUserAvatarUrl}
        />
      ),
    },
    {
      title: 'Rating',
      key: 'rating',
      width: 150,
      sorter: (a, b) => getRatingValue(a) - getRatingValue(b),
      render: (_, record) => <RatingStars rating={getRatingValue(record)} />,
    },
    {
      title: 'Nhãn',
      key: 'label',
      width: 138,
      render: (_, record) => {
        const label = getLabelMeta(record)
        return <span className={`reviews-label-badge label-${label.key}`}>{label.text}</span>
      },
    },
    {
      title: 'Ngày tạo',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 112,
      render: (date: string) => <span className="reviews-muted">{formatDate(date)}</span>,
      sorter: (a, b) => (normalizeDate(b.createdAt)?.valueOf() || 0) - (normalizeDate(a.createdAt)?.valueOf() || 0),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 148,
      align: 'right',
      render: (_, record) => (
        <Button className="reviews-view-button" icon={<EyeOutlined />} onClick={() => {
          setSelectedReview(record)
          setDrawerVisible(true)
        }}>
          Xem chi tiết
        </Button>
      ),
    },
  ]

  return (
    <MainLayout>
      <div className="reviews-page">
        <Card className="reviews-admin-card">
          <div className="reviews-page-head">
            <div className="reviews-title-wrap">
              <span className="reviews-title-icon">
                <StarFilled />
              </span>
              <div>
                <h2>Danh sách đánh giá</h2>
                <p>Quản lý và theo dõi các đánh giá của người dùng trên hệ thống.</p>
              </div>
            </div>
            <span className="reviews-total-badge">Tổng {pagination.total} đánh giá</span>
          </div>

          <div className="reviews-toolbar">
            <Input
              className="reviews-search"
              placeholder="Tìm theo hoạt động, người đánh giá hoặc người được đánh giá"
              prefix={<SearchOutlined />}
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              allowClear
            />
            <Select
              className="reviews-filter"
              value={labelFilter}
              onChange={(value) => setLabelFilter(value)}
              options={[
                { label: 'Tất cả nhãn', value: 'all' },
                { label: 'Tích cực', value: 'positive' },
                { label: 'Trung bình', value: 'medium' },
                { label: 'Cần cải thiện', value: 'improve' },
              ]}
            />
            <Select
              className="reviews-filter reviews-rating-filter"
              placeholder="Số sao"
              value={ratingFilter}
              onChange={(value) => setRatingFilter(value ?? null)}
              allowClear
              options={[
                { label: '5 sao', value: 5 },
                { label: '4 sao', value: 4 },
                { label: '3 sao', value: 3 },
                { label: '2 sao', value: 2 },
                { label: '1 sao', value: 1 },
              ]}
            />
            <Button icon={<ReloadOutlined />} onClick={refreshReviews} loading={loading} className="reviews-refresh">
              Làm mới
            </Button>
          </div>

          {error ? (
            <div className="reviews-state">
              <Empty description={error} image={Empty.PRESENTED_IMAGE_SIMPLE}>
                <Button type="primary" icon={<ReloadOutlined />} onClick={refreshReviews}>
                  Thử lại
                </Button>
              </Empty>
            </div>
          ) : loading && reviews.length === 0 ? (
            <ReviewSkeletonRows />
          ) : (
            <div className="reviews-table-shell">
              <Table
                columns={columns}
                dataSource={reviews.map((review) => ({ ...review, key: review.id }))}
                loading={loading || searchPending}
                pagination={{
                  current: pagination.current,
                  pageSize: pagination.pageSize,
                  total: pagination.total,
                  showSizeChanger: false,
                  showTotal: (total, range) => `Hiển thị ${range[0]}-${range[1]} trong ${total} đánh giá`,
                  onChange: (page) => loadReviews(page, debouncedSearch),
                }}
                scroll={{ x: 'max-content' }}
                locale={{
                  emptyText: (
                    <Empty description="Không tìm thấy kết quả phù hợp." image={Empty.PRESENTED_IMAGE_SIMPLE}>
                      <Button onClick={resetFilters}>Xóa bộ lọc</Button>
                    </Empty>
                  ),
                }}
                rowKey="id"
              />
            </div>
          )}
        </Card>

        <Drawer
          className="review-detail-drawer"
          title={
            <div className="review-detail-drawer-title">
              <span>Chi tiết đánh giá</span>
              <small>Thông tin hoạt động, người đánh giá và nội dung nhận xét</small>
            </div>
          }
          closeIcon={<CloseOutlined />}
          placement="right"
          onClose={() => {
            setDrawerVisible(false)
            setSelectedReview(null)
          }}
          open={drawerVisible}
          width={520}
        >
          {selectedReview && (
            <div className="review-detail-modern">
              <section className="review-detail-hero">
                <div className="review-detail-rating-main">
                  <RatingStars rating={getRatingValue(selectedReview)} />
                  <span className={`reviews-label-badge label-${getLabelMeta(selectedReview).key}`}>
                    {getLabelMeta(selectedReview).text}
                  </span>
                </div>
                <p>{selectedReview.comment || 'Chưa có bình luận'}</p>
              </section>

              <section className="review-detail-section">
                <h4>Hoạt động</h4>
                <div className="review-detail-info-grid">
                  <div className="review-detail-info-item">
                    <TagsOutlined />
                    <span>Tag hoạt động</span>
                    <strong>{cleanTagText(selectedReview.interestTag || selectedReview.activityName || 'Hoạt động')}</strong>
                  </div>
                  <div className="review-detail-info-item">
                    <CalendarOutlined />
                    <span>Ngày diễn ra</span>
                    <strong>{formatActivityDate(selectedReview)}</strong>
                  </div>
                  <div className="review-detail-info-item review-detail-info-wide">
                    <MessageOutlined />
                    <span>Nội dung hoạt động</span>
                    <strong>{cleanTagText(selectedReview.activityName) || '—'}</strong>
                  </div>
                </div>
              </section>

              <section className="review-detail-section">
                <h4>Người liên quan</h4>
                <div className="review-detail-people">
                  <UserCell
                    name={selectedReview.reviewerName}
                    id={selectedReview.reviewerId}
                    avatarUrl={selectedReview.reviewerAvatarUrl}
                  />
                  <UserCell
                    name={selectedReview.reviewedUserName}
                    id={selectedReview.reviewedUserId}
                    avatarUrl={selectedReview.reviewedUserAvatarUrl}
                  />
                </div>
              </section>

              <section className="review-detail-section">
                <h4>Thông tin hệ thống</h4>
                <div className="review-detail-field-list">
                  <div className="review-detail-field">
                    <span>Mã đánh giá</span>
                    <strong>#{selectedReview.id}</strong>
                  </div>
                  <div className="review-detail-field">
                    <span>Ngày tạo</span>
                    <strong>{formatDate(selectedReview.createdAt)}</strong>
                  </div>
                </div>
              </section>
            </div>
          )}
        </Drawer>
      </div>
    </MainLayout>
  )
}
