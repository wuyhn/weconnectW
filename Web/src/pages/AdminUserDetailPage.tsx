import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Card,
  Button,
  Space,
  Avatar,
  Descriptions,
  Tag,
  Empty,
  Spin,
  message,
  Row,
  Col,
  Popconfirm,
} from 'antd'
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  FileTextOutlined,
  LockOutlined,
  TeamOutlined,
  StarOutlined,
  UnlockOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import MainLayout from '../components/MainLayout'
import StatusBadge from '../components/StatusBadge'
import { userAdminService } from '../services/userAdminService'
import { User, UserStats } from '../types'
import dayjs from 'dayjs'
import './AdminUserDetailPage.css'
import { resolveAvatarUrl } from '../utils/avatar'
import { cleanTagText } from '../utils/text'

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

const formatDate = (value: unknown, pattern = 'MMMM DD, YYYY') => {
  const parsed = normalizeDate(value)
  return parsed ? parsed.format(pattern) : '-'
}

export default function AdminUserDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [user, setUser] = useState<User | null>(null)
  const [stats, setStats] = useState<UserStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState(false)

  useEffect(() => {
    if (id) {
      loadUser(parseInt(id))
    }
  }, [id])

  const loadUser = async (userId: number) => {
    try {
      setLoading(true)
      const [userData, userStats] = await Promise.all([
        userAdminService.getUser(userId),
        userAdminService.getUserStats(userId),
      ])
      setUser(userData)
      setStats(userStats)
    } catch (error: any) {
      message.error(error?.message || 'Failed to load user')
      navigate('/users')
    } finally {
      setLoading(false)
    }
  }

  const handleDeleteUser = async () => {
    if (!user) return
    try {
      setActionLoading(true)
      await userAdminService.deleteUser(user.id)
      message.success('User deleted successfully')
      navigate('/users')
    } catch (error: any) {
      message.error(error?.message || 'Failed to delete user')
    } finally {
      setActionLoading(false)
    }
  }

  const handleToggleBlockUser = async () => {
    if (!user) return
    const wasBlocked = user.isBlocked

    try {
      setActionLoading(true)
      const updatedUser = wasBlocked
        ? await userAdminService.unblockUser(user.id)
        : await userAdminService.blockUser(user.id)

      setUser((current) =>
        current
          ? {
              ...current,
              ...updatedUser,
              isBlocked: updatedUser?.isBlocked ?? !wasBlocked,
            }
          : current
      )
      message.success(wasBlocked ? 'Đã mở khóa tài khoản' : 'Đã khóa tài khoản')
    } catch (error: any) {
      message.error(error?.message || 'Không thể cập nhật trạng thái tài khoản')
    } finally {
      setActionLoading(false)
    }
  }

  if (loading) {
    return (
      <MainLayout>
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '500px' }}>
          <Spin size="large" />
        </div>
      </MainLayout>
    )
  }

  if (!user) {
    return (
      <MainLayout>
        <Card>
          <Empty description="User not found" style={{ marginTop: '48px' }} />
          <div style={{ textAlign: 'center', marginTop: '24px' }}>
            <Button type="primary" onClick={() => navigate('/users')}>
              Back to Users
            </Button>
          </div>
        </Card>
      </MainLayout>
    )
  }

  const s = stats ?? {
    totalPostsCreated: 0,
    totalActivitiesJoined: 0,
    totalReviewsReceived: 0,
    totalReportsReceived: 0,
    confirmedViolations: 0,
  }

  const allZero =
    s.totalPostsCreated === 0 &&
    s.totalActivitiesJoined === 0 &&
    s.totalReviewsReceived === 0 &&
    s.totalReportsReceived === 0

  return (
    <MainLayout>
      <div className="admin-user-detail-page">
        {/* Back Button */}
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate('/users')}
          style={{ marginBottom: '16px' }}
        >
          Back to Users
        </Button>

        {/* User Header Card */}
        <Card className="user-header-card" style={{ marginBottom: '24px' }}>
          <Row gutter={24}>
            <Col xs={24} sm={6} style={{ textAlign: 'center' }}>
              <Avatar
                size={120}
                src={resolveAvatarUrl(user.avatarUrl)}
                icon={!user.avatarUrl ? <UserOutlined /> : undefined}
                className="user-avatar"
              />
            </Col>
            <Col xs={24} sm={18}>
              <div className="user-header-content">
                <div style={{ marginBottom: '16px' }}>
                  <h1 style={{ margin: '0 0 8px 0', fontSize: '28px' }}>{user.fullName}</h1>
                  <p style={{ margin: 0, color: '#999', fontSize: '14px' }}>ID: {user.id}</p>
                </div>

                <Space direction="vertical" size="small" style={{ marginBottom: '16px' }}>
                  <div>
                    <strong>Email:</strong> {user.email}
                  </div>
                  <div>
                    <strong>Joined:</strong> {dayjs(user.createdAt).format('MMMM DD, YYYY')}
                  </div>
                  <div>
                    <strong>Status:</strong>{' '}
                    <StatusBadge
                      status={user.isBlocked ? 'blocked' : 'active'}
                      text={user.isBlocked ? 'Blocked' : 'Active'}
                    />
                  </div>
                </Space>

                {/* Action Buttons */}
                <Space>
                  <Popconfirm
                    title={user.isBlocked ? 'Mở khóa tài khoản' : 'Khóa tài khoản'}
                    description={
                      user.isBlocked
                        ? 'Mở khóa tài khoản này để người dùng có thể sử dụng lại hệ thống?'
                        : 'Khóa tài khoản này trong 7 ngày? Người dùng đang online sẽ bị đăng xuất.'
                    }
                    onConfirm={handleToggleBlockUser}
                    okText={user.isBlocked ? 'Mở khóa' : 'Khóa tài khoản'}
                    cancelText="Hủy"
                    okButtonProps={{ danger: !user.isBlocked }}
                  >
                    <Button
                      icon={user.isBlocked ? <UnlockOutlined /> : <LockOutlined />}
                      loading={actionLoading}
                      danger={!user.isBlocked}
                      type={user.isBlocked ? 'primary' : 'default'}
                    >
                      {user.isBlocked ? 'Mở khóa tài khoản' : 'Khóa tài khoản'}
                    </Button>
                  </Popconfirm>
                  <Popconfirm
                    title="Delete User"
                    description="Are you sure you want to delete this user? This action cannot be undone."
                    onConfirm={handleDeleteUser}
                    okText="Yes"
                    cancelText="No"
                    okButtonProps={{ danger: true }}
                  >
                    <Button
                      icon={<DeleteOutlined />}
                      loading={actionLoading}
                      danger
                    >
                      Delete User
                    </Button>
                  </Popconfirm>
                </Space>
              </div>
            </Col>
          </Row>
        </Card>

        {/* Tổng quan */}
        <Card
          title="Tổng quan"
          style={{ marginBottom: '24px' }}
          bodyStyle={{ paddingBottom: allZero ? '8px' : '24px' }}
        >
          <Row gutter={[16, 16]}>
            {/* Bài viết */}
            <Col xs={12} sm={6}>
              <div className="overview-stat-card overview-stat-card--blue">
                <div className="overview-stat-icon">
                  <FileTextOutlined />
                </div>
                <div className="overview-stat-value">{s.totalPostsCreated}</div>
                <div className="overview-stat-label">Bài viết đã tạo</div>
              </div>
            </Col>

            {/* Hoạt động tham gia */}
            <Col xs={12} sm={6}>
              <div className="overview-stat-card overview-stat-card--green">
                <div className="overview-stat-icon">
                  <TeamOutlined />
                </div>
                <div className="overview-stat-value">{s.totalActivitiesJoined}</div>
                <div className="overview-stat-label">Hoạt động tham gia</div>
              </div>
            </Col>

            {/* Đánh giá đã nhận */}
            <Col xs={12} sm={6}>
              <div className="overview-stat-card overview-stat-card--yellow">
                <div className="overview-stat-icon">
                  <StarOutlined />
                </div>
                <div className="overview-stat-value">{s.totalReviewsReceived}</div>
                <div className="overview-stat-label">Đánh giá đã nhận</div>
                {s.totalReviewsReceived > 0 && user.averageRating != null && (
                  <div className="overview-stat-subtext">
                    Trung bình: {user.averageRating.toFixed(1)} ★
                  </div>
                )}
              </div>
            </Col>

            {/* Báo cáo */}
            <Col xs={12} sm={6}>
              <div className={`overview-stat-card ${s.totalReportsReceived > 0 ? 'overview-stat-card--red' : 'overview-stat-card--gray'}`}>
                <div className="overview-stat-icon">
                  <WarningOutlined />
                </div>
                <div className="overview-stat-value">{s.totalReportsReceived}</div>
                <div className="overview-stat-label">Báo cáo nhận được</div>
                {s.confirmedViolations > 0 && (
                  <div className="overview-stat-subtext">
                    {s.confirmedViolations} vi phạm đã xác nhận
                  </div>
                )}
              </div>
            </Col>
          </Row>

          {allZero && (
            <div className="overview-empty-hint">
              Người dùng này chưa có hoạt động nào.
            </div>
          )}
        </Card>

        {/* Basic Information */}
        <Card title="Basic Information" style={{ marginBottom: '24px' }}>
          <Descriptions bordered size="small" layout="vertical">
            <Descriptions.Item label="User ID">{user.id}</Descriptions.Item>
            <Descriptions.Item label="Full Name">{user.fullName}</Descriptions.Item>
            <Descriptions.Item label="Email">{user.email}</Descriptions.Item>
            <Descriptions.Item label="Gender">{user.gender || '-'}</Descriptions.Item>
            <Descriptions.Item label="Birthday">
              {formatDate(user.birthday)}
            </Descriptions.Item>
            <Descriptions.Item label="Bio">{user.bio || '-'}</Descriptions.Item>
            <Descriptions.Item label="Created At">
              {formatDate(user.createdAt, 'MMMM DD, YYYY HH:mm')}
            </Descriptions.Item>
          </Descriptions>
        </Card>

        {/* Admin Information */}
        <Card title="Admin Information" style={{ marginBottom: '24px' }}>
          <Descriptions bordered size="small" layout="vertical">
            <Descriptions.Item label="Role">
              <Tag color={user.role === 1 ? 'blue' : 'default'}>
                {user.role === 1 ? 'Admin' : 'User'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Status">
              <StatusBadge
                status={user.isBlocked ? 'blocked' : 'active'}
                text={user.isBlocked ? 'Blocked' : 'Active'}
              />
            </Descriptions.Item>
            <Descriptions.Item label="Average Rating">
              {user.averageRating != null ? user.averageRating.toFixed(2) : '0'} ⭐
            </Descriptions.Item>
            <Descriptions.Item label="Reputation Score">
              <strong>{Math.round(user.reputationScore ?? 0)}</strong> / 100 điểm uy tín
            </Descriptions.Item>
            <Descriptions.Item label="Bài viết đã tạo">{s.totalPostsCreated}</Descriptions.Item>
            <Descriptions.Item label="Hoạt động tham gia">{s.totalActivitiesJoined}</Descriptions.Item>
            <Descriptions.Item label="Đánh giá đã nhận">{s.totalReviewsReceived}</Descriptions.Item>
            <Descriptions.Item label="Báo cáo nhận được">{s.totalReportsReceived}</Descriptions.Item>
          </Descriptions>
        </Card>

        {/* Interest Tags */}
        {user.interestTags && user.interestTags.length > 0 && (
          <Card title="Interest Tags">
            <Space wrap>
              {user.interestTags.map((tag) => (
                <Tag key={tag} color="blue">
                  {cleanTagText(tag)}
                </Tag>
              ))}
            </Space>
          </Card>
        )}
      </div>
    </MainLayout>
  )
}
