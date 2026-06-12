import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Avatar,
  Button,
  Card,
  Drawer,
  Dropdown,
  Empty,
  Input,
  Select,
  Spin,
  Table,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  DeleteOutlined,
  EyeOutlined,
  CloseOutlined,
  FileTextOutlined,
  LockOutlined,
  MoreOutlined,
  ReloadOutlined,
  SearchOutlined,
  StarOutlined,
  TeamOutlined,
  UnlockOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import MainLayout from '../components/MainLayout'
import LoadingState from '../components/LoadingState'
import ConfirmActionModal from '../components/ConfirmActionModal'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { userAdminService } from '../services/userAdminService'
import { PaginationParams, User, UserStats } from '../types'
import { resolveAvatarUrl } from '../utils/avatar'
import { cleanTagText } from '../utils/text'
import './UsersPage.css'

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

    const viDate = trimmed.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/)
    if (viDate) {
      const [, day, month, year] = viDate
      const parsed = dayjs(new Date(Number(year), Number(month) - 1, Number(day)))
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

const reputationTone = (score?: number) => {
  if (typeof score !== 'number') return 'medium'
  if (score >= 80) return 'high'
  if (score >= 50) return 'medium'
  return 'low'
}

const reputationLabel = (score?: number) => {
  if (typeof score !== 'number') return '-'
  if (score >= 80) return 'Cao'
  if (score >= 50) return 'Trung bình'
  return 'Thấp'
}

const roleLabel = (role?: number) => (role === 1 ? 'Quản trị viên' : 'Người dùng')

export default function UsersPage() {
  const [searchParams] = useSearchParams()
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<number | null>(null)
  // Nhận điều hướng từ Dashboard: /users?blocked=true sẽ mở sẵn bộ lọc tài khoản bị khóa.
  const [blockedFilter, setBlockedFilter] = useState<boolean | null>(() => {
    const blocked = searchParams.get('blocked')
    if (blocked === 'true') return true
    if (blocked === 'false') return false
    return null
  })
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [selectedUser, setSelectedUser] = useState<User | null>(null)
  const [drawerVisible, setDrawerVisible] = useState(false)
  const [drawerStats, setDrawerStats] = useState<UserStats | null>(null)
  const [drawerStatsLoading, setDrawerStatsLoading] = useState(false)
  const [modalConfig, setModalConfig] = useState<{
    title: string
    message: string
    onConfirm: () => void
    type?: 'warning' | 'error' | 'success' | 'info'
    okText?: string
  } | null>(null)
  const [actionLoading, setActionLoading] = useState(false)
  const debouncedSearch = useDebouncedValue(search, 300)
  const searchPending = search !== debouncedSearch

  useEffect(() => {
    loadUsers(1, debouncedSearch)
  }, [debouncedSearch, roleFilter, blockedFilter])

  const resetFilters = () => {
    setSearch('')
    setRoleFilter(null)
    setBlockedFilter(null)
  }

  const loadUsers = async (page: number, searchValue = debouncedSearch) => {
    try {
      setLoading(true)
      setError(null)
      const params: PaginationParams = { page, pageSize: pagination.pageSize }
      const result = await userAdminService.getUsers(params, {
        search: searchValue,
        role: roleFilter,
        isBlocked: blockedFilter,
      })
      setUsers(result.data)
      setPagination({ current: page, pageSize: pagination.pageSize, total: result.total })
    } catch (err) {
      setError('Không thể tải danh sách người dùng')
      message.error('Không thể tải danh sách người dùng')
    } finally {
      setLoading(false)
    }
  }

  const openUserDrawer = (user: User) => {
    setSelectedUser(user)
    setDrawerVisible(true)
    setDrawerStats(null)
    setDrawerStatsLoading(true)

    Promise.all([
      userAdminService.getUser(user.id).catch(() => user),
      userAdminService.getUserStats(user.id),
    ])
      .then(([userDetail, stats]) => {
        setSelectedUser(userDetail)
        setDrawerStats(stats)
      })
      .finally(() => setDrawerStatsLoading(false))
  }

  const openDeleteConfirm = (user: User) => {
    setModalConfig({
      title: 'Xóa người dùng',
      message: `Bạn có chắc muốn xóa ${user.fullName || user.email}? Hành động này không thể hoàn tác.`,
      onConfirm: () => handleDeleteUser(user.id),
      type: 'error',
      okText: 'Xóa người dùng',
    })
  }

  const openToggleBlockConfirm = (user: User) => {
    setModalConfig({
      title: user.isBlocked ? 'Mở khóa tài khoản' : 'Khóa tài khoản',
      message: user.isBlocked
        ? `Mở khóa tài khoản ${user.fullName || user.email}? Người dùng sẽ có thể sử dụng lại các chức năng.`
        : `Khóa tài khoản ${user.fullName || user.email}? Người dùng sẽ bị hạn chế sử dụng hệ thống.`,
      onConfirm: () => handleToggleBlockUser(user),
      type: user.isBlocked ? 'info' : 'warning',
      okText: user.isBlocked ? 'Mở khóa' : 'Khóa tài khoản',
    })
  }

  const handleDeleteUser = async (userId: number) => {
    try {
      setActionLoading(true)
      await userAdminService.deleteUser(userId)
      message.success('Đã xóa người dùng')
      setModalConfig(null)
      if (selectedUser?.id === userId) {
        setDrawerVisible(false)
        setSelectedUser(null)
      }
      loadUsers(pagination.current)
    } catch (err: any) {
      message.error(err?.message || 'Không thể xóa người dùng')
    } finally {
      setActionLoading(false)
    }
  }

  const handleToggleBlockUser = async (user: User) => {
    const wasBlocked = user.isBlocked

    try {
      setActionLoading(true)
      const updatedUser = wasBlocked
        ? await userAdminService.unblockUser(user.id)
        : await userAdminService.blockUser(user.id)
      const nextBlockedState = updatedUser?.isBlocked ?? !wasBlocked

      setUsers((prev) =>
        prev.map((item) =>
          item.id === user.id
            ? { ...item, ...updatedUser, isBlocked: nextBlockedState }
            : item
        )
      )
      setSelectedUser((prev) =>
        prev && prev.id === user.id
          ? { ...prev, ...updatedUser, isBlocked: nextBlockedState }
          : prev
      )
      setModalConfig(null)
      message.success(wasBlocked ? 'Đã mở khóa tài khoản' : 'Đã khóa tài khoản')
      await loadUsers(pagination.current)
    } catch (err: any) {
      message.error(err?.message || 'Không thể cập nhật trạng thái tài khoản')
    } finally {
      setActionLoading(false)
    }
  }

  const columns: ColumnsType<User> = [
    {
      title: 'Người dùng',
      dataIndex: 'fullName',
      key: 'user',
      width: 280,
      ellipsis: true,
      render: (_: string, record) => (
        <div className="users-user-cell">
          <Avatar
            size={38}
            src={resolveAvatarUrl(record.avatarUrl)}
            icon={!record.avatarUrl ? <UserOutlined /> : undefined}
            className="users-avatar"
          />
          <div className="users-user-meta">
            <span className="users-user-name" title={record.fullName}>
              {record.fullName || 'Người dùng'}
            </span>
            <span className="users-user-email" title={record.email}>
              {record.email}
            </span>
          </div>
        </div>
      ),
    },
    {
      title: 'Giới tính',
      dataIndex: 'gender',
      key: 'gender',
      width: 92,
      render: (gender: string) => <span className="users-muted">{gender || '-'}</span>,
    },
    {
      title: 'Đánh giá',
      dataIndex: 'averageRating',
      key: 'averageRating',
      width: 96,
      sorter: (a, b) => (a.averageRating || 0) - (b.averageRating || 0),
      render: (rating: number) => (
        <span className="users-rating">
          {(rating || 0).toFixed(1)}
        </span>
      ),
    },
    {
      title: 'Uy tín',
      dataIndex: 'reputationScore',
      key: 'reputationScore',
      width: 132,
      sorter: (a, b) => (a.reputationScore || 0) - (b.reputationScore || 0),
      render: (score: number) => (
        <span className={`users-soft-badge reputation-${reputationTone(score)}`}>
          {score ?? '-'} · {reputationLabel(score)}
        </span>
      ),
    },
    {
      title: 'Vai trò',
      dataIndex: 'role',
      key: 'role',
      width: 104,
      render: (role: number) => (
        <span className={`users-soft-badge role-${role === 1 ? 'admin' : 'user'}`}>
          {roleLabel(role)}
        </span>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'isBlocked',
      key: 'isBlocked',
      width: 124,
      render: (isBlocked: boolean) => (
        <span className={`users-status-badge ${isBlocked ? 'users-status-blocked' : 'users-status-active'}`}>
          <span className="users-status-dot" aria-hidden="true" />
          {isBlocked ? 'Bị khóa' : 'Hoạt động'}
        </span>
      ),
    },
    {
      title: 'Ngày tham gia',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 118,
      sorter: (a, b) => {
        const bTime = normalizeDate(b.createdAt)?.valueOf() || 0
        const aTime = normalizeDate(a.createdAt)?.valueOf() || 0
        return bTime - aTime
      },
      render: (date: string) => <span className="users-muted">{formatDate(date)}</span>,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 118,
      align: 'right',
      render: (_, record) => (
        <div className="users-actions">
          <Button type="primary" size="small" icon={<EyeOutlined />} onClick={() => openUserDrawer(record)}>
            Xem
          </Button>
          <Dropdown
            trigger={['click']}
            placement="bottomRight"
            menu={{
              items: [
                {
                  key: 'delete',
                  icon: <DeleteOutlined />,
                  label: 'Xóa người dùng',
                  danger: true,
                  onClick: () => openDeleteConfirm(record),
                },
              ],
            }}
          >
            <Button size="small" className="users-more-button" icon={<MoreOutlined />} />
          </Dropdown>
        </div>
      ),
    },
  ]

  if (loading && users.length === 0 && !error) {
    return <LoadingState fullPage message="Đang tải danh sách người dùng..." />
  }

  return (
    <MainLayout>
      <div className="users-page">
        <Card className="users-card">
          <div className="users-page-head">
            <div>
              <h2>Danh sách người dùng</h2>
              <p>Quản lý hồ sơ, trạng thái và uy tín của người dùng WeConnect.</p>
            </div>
          </div>

          <div className="users-toolbar">
            <Input
              className="users-search"
              placeholder="Tìm theo tên hoặc email"
              prefix={<SearchOutlined />}
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              allowClear
            />
            <Select
              className="users-filter"
              placeholder="Vai trò"
              value={roleFilter}
              onChange={(value) => setRoleFilter(value ?? null)}
              allowClear
              options={[
                { label: 'Người dùng', value: 0 },
                { label: 'Quản trị viên', value: 1 },
              ]}
            />
            <Select
              className="users-filter"
              placeholder="Trạng thái"
              value={blockedFilter}
              onChange={(value) => setBlockedFilter(value ?? null)}
              allowClear
              options={[
                { label: 'Hoạt động', value: false },
                { label: 'Bị khóa', value: true },
              ]}
            />
            <Button icon={<ReloadOutlined />} onClick={() => loadUsers(1, search)} loading={loading} className="users-refresh">
              Làm mới
            </Button>
          </div>

          {error ? (
            <div className="users-state">
              <Empty description={error} image={Empty.PRESENTED_IMAGE_SIMPLE}>
                <Button type="primary" icon={<ReloadOutlined />} onClick={() => loadUsers(1, search)}>
                  Thử lại
                </Button>
              </Empty>
            </div>
          ) : (
            <div className="users-table-shell">
              <Table
                columns={columns}
                dataSource={users.map((user) => ({ ...user, key: user.id }))}
                loading={loading || searchPending}
                pagination={{
                  current: pagination.current,
                  pageSize: pagination.pageSize,
                  total: pagination.total,
                  showSizeChanger: false,
                  showTotal: (total, range) => `${range[0]}-${range[1]} / ${total} người dùng`,
                  onChange: (page) => loadUsers(page, debouncedSearch),
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
          className="user-detail-drawer"
          title={
            <div className="user-detail-drawer-title">
              <span>Chi tiết người dùng</span>
              <small>Thông tin hồ sơ, trạng thái và uy tín người dùng</small>
            </div>
          }
          closeIcon={<CloseOutlined />}
          placement="right"
          onClose={() => {
            setDrawerVisible(false)
            setSelectedUser(null)
          }}
          open={drawerVisible}
          width={500}
        >
          {selectedUser && (
            <div className="user-detail">
              <section className="user-detail-hero">
                <Avatar
                  size={88}
                  src={resolveAvatarUrl(selectedUser.avatarUrl)}
                  icon={!selectedUser.avatarUrl ? <UserOutlined /> : undefined}
                  className="user-detail-avatar"
                />
                <div className="user-detail-hero-main">
                  <h3>{selectedUser.fullName || 'Người dùng'}</h3>
                  <p>{selectedUser.email || '—'}</p>
                  <span>ID #{selectedUser.id}</span>
                </div>
                <div className="user-detail-badges">
                  <span className={`users-soft-badge role-${selectedUser.role === 1 ? 'admin' : 'user'}`}>
                    {roleLabel(selectedUser.role)}
                  </span>
                  <span className={`users-status-badge ${selectedUser.isBlocked ? 'users-status-blocked' : 'users-status-active'}`}>
                    <span className="users-status-dot" aria-hidden="true" />
                    {selectedUser.isBlocked ? 'Bị khóa' : 'Hoạt động'}
                  </span>
                  <span className={`users-soft-badge reputation-${reputationTone(selectedUser.reputationScore)}`}>
                    {selectedUser.reputationScore ?? '-'} · {reputationLabel(selectedUser.reputationScore)}
                  </span>
                </div>
              </section>

              <section className="user-detail-section">
                <h4>Thông tin cá nhân</h4>
                <div className="user-detail-field-list">
                  <div className="user-detail-field">
                    <span>Email</span>
                    <strong>{selectedUser.email || '—'}</strong>
                  </div>
                  <div className="user-detail-field">
                    <span>Ngày sinh</span>
                    <strong>{selectedUser.birthday ? formatDate(selectedUser.birthday) : '—'}</strong>
                  </div>
                  <div className="user-detail-field">
                    <span>Giới tính</span>
                    <strong>{selectedUser.gender || '—'}</strong>
                  </div>
                  <div className="user-detail-field user-detail-field-full">
                    <span>Giới thiệu</span>
                    <strong>{selectedUser.bio || 'Chưa có giới thiệu'}</strong>
                  </div>
                  <div className="user-detail-field user-detail-field-full">
                    <span>Sở thích</span>
                    {selectedUser.interestTags && selectedUser.interestTags.length > 0 ? (
                      <div className="user-detail-tags">
                        {selectedUser.interestTags.map((tag) => (
                          <span className="user-detail-tag" key={tag}>
                            {cleanTagText(tag)}
                          </span>
                        ))}
                      </div>
                    ) : (
                      <strong>Chưa cập nhật sở thích</strong>
                    )}
                  </div>
                </div>
              </section>

              <section className="user-detail-section">
                <h4>Thông tin tài khoản</h4>
                <div className="user-detail-stat-grid">
                  <div className="user-detail-stat">
                    <span>Điểm uy tín</span>
                    <strong className={`stat-tone-${reputationTone(selectedUser.reputationScore)}`}>
                      {selectedUser.reputationScore ?? '-'} · {reputationLabel(selectedUser.reputationScore)}
                    </strong>
                  </div>
                  <div className="user-detail-stat">
                    <span>Đánh giá trung bình</span>
                    <strong className="user-detail-rating">
                      {(selectedUser.averageRating || 0).toFixed(1)}
                    </strong>
                  </div>
                  <div className="user-detail-stat">
                    <span>Vai trò</span>
                    <strong>{roleLabel(selectedUser.role)}</strong>
                  </div>
                  <div className="user-detail-stat">
                    <span>Trạng thái</span>
                    <strong>{selectedUser.isBlocked ? 'Bị khóa' : 'Hoạt động'}</strong>
                  </div>
                  <div className="user-detail-stat user-detail-stat-wide">
                    <span>Ngày tham gia</span>
                    <strong>{formatDate(selectedUser.createdAt)}</strong>
                  </div>
                </div>
              </section>

              <section className="user-detail-section">
                <h4>Tổng quan</h4>
                {drawerStatsLoading ? (
                  <div className="user-detail-overview-loading">
                    <Spin size="small" />
                  </div>
                ) : (() => {
                  const s = drawerStats ?? { totalPostsCreated: 0, totalActivitiesJoined: 0, totalReviewsReceived: 0, totalReportsReceived: 0, confirmedViolations: 0 }
                  const allZero = s.totalPostsCreated === 0 && s.totalActivitiesJoined === 0 && s.totalReviewsReceived === 0 && s.totalReportsReceived === 0
                  return (
                    <>
                      <div className="user-detail-overview-grid">
                        <div className="overview-card overview-card--blue">
                          <FileTextOutlined className="overview-card-icon" />
                          <strong>{s.totalPostsCreated}</strong>
                          <span>Bài viết đã tạo</span>
                        </div>
                        <div className="overview-card overview-card--green">
                          <TeamOutlined className="overview-card-icon" />
                          <strong>{s.totalActivitiesJoined}</strong>
                          <span>Hoạt động tham gia</span>
                        </div>
                        <div className="overview-card overview-card--yellow">
                          <StarOutlined className="overview-card-icon" />
                          <strong>{s.totalReviewsReceived}</strong>
                          <span>Đánh giá đã nhận</span>
                          {s.totalReviewsReceived > 0 && selectedUser.averageRating != null && (
                            <em>Trung bình: {selectedUser.averageRating.toFixed(1)} ★</em>
                          )}
                        </div>
                        <div className={`overview-card ${s.totalReportsReceived > 0 ? 'overview-card--red' : 'overview-card--gray'}`}>
                          <WarningOutlined className="overview-card-icon" />
                          <strong>{s.totalReportsReceived}</strong>
                          <span>Báo cáo nhận được</span>
                          {s.confirmedViolations > 0 && (
                            <em>{s.confirmedViolations} vi phạm đã xác nhận</em>
                          )}
                        </div>
                      </div>
                      {allZero && <p className="user-detail-empty-note">Người dùng này chưa có hoạt động nào.</p>}
                    </>
                  )
                })()}
              </section>

              <section className="user-detail-actions-card">
                <h4>Hành động admin</h4>
                <div className="user-detail-actions">
                  <Button onClick={() => setDrawerVisible(false)}>Đóng</Button>
                  <Button
                    className={selectedUser.isBlocked ? 'user-action-success' : 'user-action-warning'}
                    icon={selectedUser.isBlocked ? <UnlockOutlined /> : <LockOutlined />}
                    onClick={() => openToggleBlockConfirm(selectedUser)}
                  >
                    {selectedUser.isBlocked ? 'Mở khóa tài khoản' : 'Khóa tài khoản'}
                  </Button>
                  <Button danger icon={<DeleteOutlined />} onClick={() => openDeleteConfirm(selectedUser)}>
                    Xóa người dùng
                  </Button>
                </div>
              </section>
            </div>
          )}
        </Drawer>

        {modalConfig && (
          <ConfirmActionModal
            open
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
