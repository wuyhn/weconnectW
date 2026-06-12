import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Avatar,
  Button,
  Card,
  Drawer,
  Empty,
  Input,
  Select,
  Table,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  CalendarOutlined,
  CloseOutlined,
  DeleteOutlined,
  EnvironmentOutlined,
  EyeOutlined,
  FileImageOutlined,
  FileTextOutlined,
  ReloadOutlined,
  SearchOutlined,
  TagsOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import MainLayout from '../components/MainLayout'
import LoadingState from '../components/LoadingState'
import ConfirmActionModal from '../components/ConfirmActionModal'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { postAdminService } from '../services/postAdminService'
import { userAdminService } from '../services/userAdminService'
import { Post, User } from '../types'
import { cleanTagText, matchesSearchQuery } from '../utils/text'
import { resolveAvatarUrl } from '../utils/avatar'
import './PostsPage.css'

const PAGE_SIZE = 10

type PostStatusKey = 'active' | 'archived' | 'ended' | 'cancelled'
type StatusFilter = 'all' | 'active' | 'archived' | 'ended'

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

const formatDate = (value: unknown, pattern = 'DD/MM/YYYY') => {
  const parsed = normalizeDate(value)
  return parsed ? parsed.format(pattern) : '-'
}

const formatActivityTime = (post: Post) => {
  const start = normalizeDate(post.startTime)
  const end = normalizeDate(post.activityEndTime || post.endTime)

  if (start && end && !start.isSame(end, 'day')) {
    return `${start.format('DD/MM/YYYY')} - ${end.format('DD/MM/YYYY')}`
  }

  if (start) return start.format('DD/MM/YYYY · HH:mm')
  if (end) return end.format('DD/MM/YYYY · HH:mm')
  return '-'
}

const resolvePostImage = (url?: string | null) => {
  if (!url || typeof url !== 'string') return undefined
  const trimmed = url.trim()
  if (!trimmed || trimmed.startsWith('content://')) return undefined

  if (trimmed.startsWith('/uploads/')) return trimmed

  const uploadsMatch = trimmed.match(/(\/uploads\/[^?#\s]+)/)
  if (uploadsMatch) return uploadsMatch[1]

  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) return trimmed

  return trimmed
}

const getPostStatus = (post: Post): { key: PostStatusKey; label: string } => {
  const end = normalizeDate(post.activityEndTime || post.endTime)

  if (post.cancelled) return { key: 'cancelled', label: 'Đã hủy' }
  if (post.archived) return { key: 'archived', label: 'Đã lưu trữ' }
  if (post.expired || (end && end.isBefore(dayjs()))) return { key: 'ended', label: 'Đã kết thúc' }
  return { key: 'active', label: 'Đang hoạt động' }
}

const getPostTitle = (content?: string) => {
  const trimmed = content?.trim()
  return trimmed || 'Bài viết chưa có nội dung'
}

const getAuthorName = (post: Post, userMap: Record<number, User>) => {
  return post.authorName || userMap[post.authorId]?.fullName || `Người dùng #${post.authorId}`
}

const getPostSearchFields = (post: Post) => [post.content]

const formatMemberCount = (post: Post) => {
  const current = typeof post.memberCount === 'number' ? post.memberCount : '-'
  return `${current} / ${post.maxMembers || '-'}`
}

export default function PostsPage() {
  const [searchParams] = useSearchParams()
  const [allPosts, setAllPosts] = useState<Post[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  // Nhận điều hướng từ Dashboard: /posts?status=archived sẽ mở sẵn bộ lọc bài đã lưu trữ.
  const [statusFilter, setStatusFilter] = useState<StatusFilter>(() => {
    const status = searchParams.get('status')
    return status === 'active' || status === 'archived' || status === 'ended' ? status : 'all'
  })
  const [tagFilter, setTagFilter] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const [pagination, setPagination] = useState({ current: 1, pageSize: PAGE_SIZE, total: 0 })
  const [selectedPost, setSelectedPost] = useState<Post | null>(null)
  const [drawerVisible, setDrawerVisible] = useState(false)
  const [modalConfig, setModalConfig] = useState<{
    title: string
    message: string
    onConfirm: () => void
    type?: 'warning' | 'error' | 'success' | 'info'
    okText?: string
  } | null>(null)
  const [actionLoading, setActionLoading] = useState(false)
  const [userMap, setUserMap] = useState<Record<number, User>>({})
  const debouncedSearch = useDebouncedValue(search, 300)
  const searchPending = search !== debouncedSearch

  useEffect(() => {
    loadPosts()
  }, [refreshKey])

  useEffect(() => {
    loadUsers()
  }, [])

  const availableTags = useMemo(
    () =>
      Array.from(
        new Set(allPosts.map((post) => post.interestTag).filter((tag): tag is string => Boolean(tag?.trim())))
      ).sort((a, b) => cleanTagText(a).localeCompare(cleanTagText(b))),
    [allPosts]
  )

  const filteredPosts = useMemo(() => {
    let filtered = [...allPosts]

    if (debouncedSearch.trim()) {
      filtered = filtered.filter((post) => matchesSearchQuery(getPostSearchFields(post), debouncedSearch))
    }

    if (statusFilter !== 'all') {
      filtered = filtered.filter((post) => getPostStatus(post).key === statusFilter)
    }

    if (tagFilter) {
      filtered = filtered.filter((post) => post.interestTag === tagFilter)
    }

    return filtered
  }, [allPosts, debouncedSearch, statusFilter, tagFilter])

  const paginatedPosts = useMemo(() => {
    const start = (pagination.current - 1) * PAGE_SIZE
    return filteredPosts.slice(start, start + PAGE_SIZE)
  }, [filteredPosts, pagination.current])

  useEffect(() => {
    setPagination((prev) => (prev.current === 1 ? prev : { ...prev, current: 1 }))
  }, [search, statusFilter, tagFilter])

  useEffect(() => {
    const maxPage = Math.max(1, Math.ceil(filteredPosts.length / PAGE_SIZE))
    setPagination((prev) => {
      const current = Math.min(prev.current, maxPage)
      if (prev.current === current && prev.total === filteredPosts.length && prev.pageSize === PAGE_SIZE) {
        return prev
      }
      return { current, pageSize: PAGE_SIZE, total: filteredPosts.length }
    })
  }, [filteredPosts.length])

  const loadUsers = async () => {
    try {
      const result = await userAdminService.getUsers({ page: 1, pageSize: 1000 })
      const map: Record<number, User> = {}
      result.data.forEach((user) => {
        map[user.id] = user
      })
      setUserMap(map)
    } catch (err) {
      console.error('Không thể tải danh sách tác giả', err)
    }
  }

  const loadPosts = async () => {
    try {
      setLoading(true)
      setError(null)

      const result = await postAdminService.getPosts({ page: 1, pageSize: 10000 })
      setAllPosts(result.data)
    } catch (err) {
      setError('Không thể tải danh sách bài viết')
      message.error('Không thể tải danh sách bài viết')
    } finally {
      setLoading(false)
    }
  }

  const refreshPosts = () => {
    setRefreshKey((value) => value + 1)
  }

  const resetFilters = () => {
    setSearch('')
    setStatusFilter('all')
    setTagFilter(null)
    refreshPosts()
  }

  const openPostDrawer = async (post: Post) => {
    setSelectedPost(post)
    setDrawerVisible(true)

    try {
      const detail = await postAdminService.getPost(post.id)
      setSelectedPost(detail)
    } catch {
      // Giữ dữ liệu từ danh sách nếu API chi tiết tạm thời không phản hồi.
    }
  }

  const openDeleteConfirm = (post: Post) => {
    setModalConfig({
      title: 'Xóa bài viết',
      message: `Bạn có chắc muốn xóa bài viết “${getPostTitle(post.content).slice(0, 72)}”? Hành động này không thể hoàn tác.`,
      onConfirm: () => handleDeletePost(post.id),
      type: 'error',
      okText: 'Xóa bài viết',
    })
  }

  const handleDeletePost = async (postId: number) => {
    try {
      setActionLoading(true)
      await postAdminService.deletePost(postId)
      message.success('Đã xóa bài viết')

      if (selectedPost?.id === postId) {
        setDrawerVisible(false)
        setSelectedPost(null)
      }

      setModalConfig(null)
      setAllPosts((current) => current.filter((post) => post.id !== postId))
      if (paginatedPosts.length === 1 && pagination.current > 1) {
        setPagination((current) => ({ ...current, current: current.current - 1 }))
      }
    } catch (err: any) {
      message.error(err?.message || 'Không thể xóa bài viết')
    } finally {
      setActionLoading(false)
    }
  }

  const columns: ColumnsType<Post> = [
    {
      title: 'Bài viết',
      key: 'post',
      width: 340,
      render: (_, record) => {
        const imageUrl = resolvePostImage(record.imageUrl)
        const authorName = getAuthorName(record, userMap)

        return (
          <div className="posts-post-cell">
            <div className="posts-thumbnail">
              {imageUrl ? (
                <img
                  src={imageUrl}
                  alt="Ảnh bài viết"
                  onError={(event) => {
                    event.currentTarget.style.display = 'none'
                  }}
                />
              ) : (
                <FileImageOutlined />
              )}
            </div>
            <div className="posts-post-meta">
              <span className="posts-post-title" title={getPostTitle(record.content)}>
                {getPostTitle(record.content)}
              </span>
              <div className="posts-post-subline">
                <span className="posts-tag-chip">{cleanTagText(record.interestTag)}</span>
                <span className="posts-author" title={authorName}>
                  Tác giả: {authorName}
                </span>
              </div>
            </div>
          </div>
        )
      },
    },
    {
      title: 'Vị trí',
      dataIndex: 'location',
      key: 'location',
      width: 180,
      render: (location: string) => (
        <span className="posts-location" title={location || '-'}>
          {location || '-'}
        </span>
      ),
    },
    {
      title: 'Thành viên',
      key: 'members',
      width: 104,
      render: (_, record) => (
        <span className="posts-member-count">
          {formatMemberCount(record)}
        </span>
      ),
    },
    {
      title: 'Thời gian hoạt động',
      key: 'activityTime',
      width: 168,
      render: (_, record) => <span className="posts-muted">{formatActivityTime(record)}</span>,
    },
    {
      title: 'Ngày tạo',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 116,
      sorter: (a, b) => (normalizeDate(b.createdAt)?.valueOf() || 0) - (normalizeDate(a.createdAt)?.valueOf() || 0),
      render: (date: string) => <span className="posts-muted">{formatDate(date)}</span>,
    },
    {
      title: 'Trạng thái',
      key: 'status',
      width: 132,
      render: (_, record) => {
        const status = getPostStatus(record)
        return (
          <span className={`posts-status-badge posts-status-${status.key}`}>
            <span className="posts-status-dot" aria-hidden="true" />
            {status.label}
          </span>
        )
      },
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 138,
      align: 'right',
      render: (_, record) => (
        <div className="posts-actions">
          <Button type="primary" size="small" icon={<EyeOutlined />} onClick={() => openPostDrawer(record)}>
            Xem
          </Button>
          <Button danger size="small" icon={<DeleteOutlined />} onClick={() => openDeleteConfirm(record)}>
            Xóa
          </Button>
        </div>
      ),
    },
  ]

  if (loading && allPosts.length === 0 && !error) {
    return <LoadingState fullPage message="Đang tải danh sách bài viết..." />
  }

  return (
    <MainLayout>
      <div className="posts-page">
        <Card className="posts-card">
          <div className="posts-page-head">
            <div>
              <h2>Quản lý bài viết</h2>
              <p>Quản lý bài đăng hoạt động, trạng thái và thông tin liên quan.</p>
            </div>
            <span className="posts-total-badge">Tổng {filteredPosts.length} bài viết</span>
          </div>

          <div className="posts-toolbar">
            <Input
              className="posts-search"
              placeholder="Tìm theo tiêu đề hoặc nội dung bài viết"
              prefix={<SearchOutlined />}
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              allowClear
            />
            <Select
              className="posts-filter"
              value={statusFilter}
              onChange={(value) => setStatusFilter(value)}
              options={[
                { label: 'Tất cả trạng thái', value: 'all' },
                { label: 'Đang hoạt động', value: 'active' },
                { label: 'Đã lưu trữ', value: 'archived' },
                { label: 'Đã kết thúc', value: 'ended' },
              ]}
            />
            <Select
              className="posts-filter"
              placeholder="Tag hoạt động"
              value={tagFilter}
              onChange={(value) => setTagFilter(value ?? null)}
              allowClear
              options={availableTags.map((tag) => ({ label: cleanTagText(tag), value: tag }))}
            />
            <Button icon={<ReloadOutlined />} onClick={resetFilters} loading={loading} className="posts-refresh">
              Làm mới
            </Button>
          </div>

          {error ? (
            <div className="posts-state">
              <Empty description={error} image={Empty.PRESENTED_IMAGE_SIMPLE}>
                <Button type="primary" icon={<ReloadOutlined />} onClick={refreshPosts}>
                  Thử lại
                </Button>
              </Empty>
            </div>
          ) : (
            <div className="posts-table-shell">
              <Table
                columns={columns}
                dataSource={paginatedPosts.map((post) => ({ ...post, key: post.id }))}
                loading={loading || searchPending}
                pagination={{
                  current: pagination.current,
                  pageSize: pagination.pageSize,
                  total: filteredPosts.length,
                  showSizeChanger: false,
                  showTotal: (total, range) => `${range[0]}-${range[1]} / ${total} bài viết`,
                  onChange: (page) => setPagination((current) => ({ ...current, current: page })),
                }}
                scroll={{ x: 'max-content' }}
                locale={{
                  emptyText: (
                    <Empty
                      description={
                        <div className="posts-empty-copy">
                          <strong>Không tìm thấy kết quả phù hợp.</strong>
                          <span>Thử đổi từ khóa hoặc xóa bộ lọc hiện tại.</span>
                        </div>
                      }
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                    >
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
          className="post-detail-drawer"
          title={
            <div className="post-detail-drawer-title">
              <span>Chi tiết bài viết</span>
              <small>Thông tin nội dung, tác giả, trạng thái và hoạt động liên quan</small>
            </div>
          }
          closeIcon={<CloseOutlined />}
          placement="right"
          onClose={() => {
            setDrawerVisible(false)
            setSelectedPost(null)
          }}
          open={drawerVisible}
          width={540}
        >
          {selectedPost && (
            <div className="post-detail">
              <section className="post-detail-hero">
                <div className="post-detail-image">
                  {resolvePostImage(selectedPost.imageUrl) ? (
                    <img
                      src={resolvePostImage(selectedPost.imageUrl)}
                      alt="Ảnh bài viết"
                      onError={(event) => {
                        event.currentTarget.style.display = 'none'
                      }}
                    />
                  ) : (
                    <FileImageOutlined />
                  )}
                </div>
                <div className="post-detail-hero-content">
                  <span className={`posts-status-badge posts-status-${getPostStatus(selectedPost).key}`}>
                    <span className="posts-status-dot" aria-hidden="true" />
                    {getPostStatus(selectedPost).label}
                  </span>
                  <h3>{getPostTitle(selectedPost.content)}</h3>
                  <div className="post-detail-meta-line">
                    <TagsOutlined />
                    <span>{cleanTagText(selectedPost.interestTag)}</span>
                    <span className="post-detail-separator">•</span>
                    <Avatar
                      size={18}
                      src={resolveAvatarUrl(selectedPost.authorAvatarUrl || userMap[selectedPost.authorId]?.avatarUrl)}
                      icon={<UserOutlined />}
                      style={{ background: '#eef2f7', color: '#6b7280', flexShrink: 0, border: 0, boxShadow: 'none' }}
                    />
                    <span>{getAuthorName(selectedPost, userMap)}</span>
                  </div>
                </div>
              </section>

              <section className="post-detail-section">
                <h4>Thông tin hoạt động</h4>
                <div className="post-detail-info-grid">
                  <div className="post-detail-info-item post-detail-info-wide">
                    <EnvironmentOutlined />
                    <span>Vị trí</span>
                    <strong>{selectedPost.location || '-'}</strong>
                  </div>
                  <div className="post-detail-info-item">
                    <TeamOutlined />
                    <span>Thành viên</span>
                    <strong>{formatMemberCount(selectedPost)}</strong>
                  </div>
                  <div className="post-detail-info-item">
                    <CalendarOutlined />
                    <span>Thời gian hoạt động</span>
                    <strong>{formatActivityTime(selectedPost)}</strong>
                  </div>
                  <div className="post-detail-info-item">
                    <FileTextOutlined />
                    <span>Ngày tạo</span>
                    <strong>{formatDate(selectedPost.createdAt)}</strong>
                  </div>
                  <div className="post-detail-info-item">
                    <TagsOutlined />
                    <span>Tag</span>
                    <strong>{cleanTagText(selectedPost.interestTag)}</strong>
                  </div>
                </div>
              </section>

              <section className="post-detail-section">
                <h4>Nội dung đầy đủ</h4>
                <p className="post-detail-content">{selectedPost.content || 'Chưa có nội dung'}</p>
              </section>

              <section className="post-detail-section">
                <h4>Thông tin bài viết</h4>
                <div className="post-detail-field-list">
                  <div className="post-detail-field">
                    <span>Tác giả (Chủ phòng)</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <Avatar
                        size={28}
                        src={resolveAvatarUrl(selectedPost.authorAvatarUrl || userMap[selectedPost.authorId]?.avatarUrl)}
                        icon={<UserOutlined />}
                        style={{ background: '#eef2f7', color: '#6b7280', flexShrink: 0, border: 0, boxShadow: 'none' }}
                      />
                      <strong>{getAuthorName(selectedPost, userMap)}</strong>
                    </div>
                  </div>
                  <div className="post-detail-field">
                    <span>Mã bài viết</span>
                    <strong>#{selectedPost.id}</strong>
                  </div>
                  <div className="post-detail-field">
                    <span>Bắt đầu</span>
                    <strong>{formatDate(selectedPost.startTime, 'DD/MM/YYYY · HH:mm')}</strong>
                  </div>
                  <div className="post-detail-field">
                    <span>Kết thúc</span>
                    <strong>{formatDate(selectedPost.activityEndTime || selectedPost.endTime, 'DD/MM/YYYY · HH:mm')}</strong>
                  </div>
                </div>
              </section>

              <section className="post-detail-actions-card">
                <h4>Hành động admin</h4>
                <div className="post-detail-actions">
                  <Button onClick={() => setDrawerVisible(false)}>Đóng</Button>
                  <Button danger icon={<DeleteOutlined />} onClick={() => openDeleteConfirm(selectedPost)}>
                    Xóa bài viết
                  </Button>
                </div>
              </section>
            </div>
          )}
        </Drawer>

        {modalConfig && (
          <ConfirmActionModal
            title={modalConfig.title}
            message={modalConfig.message}
            onConfirm={modalConfig.onConfirm}
            onCancel={() => setModalConfig(null)}
            loading={actionLoading}
            type={modalConfig.type || 'warning'}
            okText={modalConfig.okText || 'Xác nhận'}
            cancelText="Hủy"
          />
        )}
      </div>
    </MainLayout>
  )
}
