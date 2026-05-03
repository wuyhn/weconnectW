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
  Statistic,
} from 'antd'
import { ArrowLeftOutlined, DeleteOutlined } from '@ant-design/icons'
import MainLayout from '../components/MainLayout'
import StatusBadge from '../components/StatusBadge'
import { userAdminService } from '../services/userAdminService'
import { postAdminService } from '../services/postAdminService'
import { reviewAdminService } from '../services/reviewAdminService'
import { User } from '../types'
import dayjs from 'dayjs'
import './AdminUserDetailPage.css'

export default function AdminUserDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [postCount, setPostCount] = useState(0)
  const [reviewCount, setReviewCount] = useState(0)
  const [actionLoading, setActionLoading] = useState(false)

  useEffect(() => {
    if (id) {
      loadUser(id)
    }
  }, [id])

  const loadUser = async (userId: string) => {
    try {
      setLoading(true)
      const userData = await userAdminService.getUser(userId)
      setUser(userData)

      // Load post count
      const postsResult = await postAdminService.getPosts({ page: 1, pageSize: 10000 })
      const userPosts = postsResult.data.filter((p) => p.authorId === userId)
      setPostCount(userPosts.length)

      // Load review count
      const reviewsResult = await reviewAdminService.getReviews({ page: 1, pageSize: 10000 })
      const userReviews = reviewsResult.data.filter(
        (r) => r.reviewerId === userId || r.reviewedUserId === userId
      )
      setReviewCount(userReviews.length)
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
              <Avatar size={120} src={user.avatarUrl} icon={undefined} className="user-avatar" />
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

        {/* Stats Row */}
        <Row gutter={16} style={{ marginBottom: '24px' }}>
          <Col xs={24} sm={8}>
            <Card>
              <Statistic
                title="Total Posts"
                value={postCount}
                valueStyle={{ color: '#1890ff' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={8}>
            <Card>
              <Statistic
                title="Total Reviews"
                value={reviewCount}
                valueStyle={{ color: '#52c41a' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={8}>
            <Card>
              <Statistic
                title="Rating"
                value={user.averageRating?.toFixed(1) || 0}
                suffix="⭐"
                valueStyle={{ color: '#faad14' }}
              />
            </Card>
          </Col>
        </Row>

        {/* Basic Information */}
        <Card title="Basic Information" style={{ marginBottom: '24px' }}>
          <Descriptions bordered size="small" layout="vertical">
            <Descriptions.Item label="User ID">{user.id}</Descriptions.Item>
            <Descriptions.Item label="Full Name">{user.fullName}</Descriptions.Item>
            <Descriptions.Item label="Email">{user.email}</Descriptions.Item>
            <Descriptions.Item label="Gender">{user.gender || '-'}</Descriptions.Item>
            <Descriptions.Item label="Birthday">
              {user.birthday ? dayjs(user.birthday).format('MMMM DD, YYYY') : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="Bio">{user.bio || '-'}</Descriptions.Item>
            <Descriptions.Item label="Created At">
              {dayjs(user.createdAt).format('MMMM DD, YYYY HH:mm')}
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
              {user.averageRating?.toFixed(2) || 0} ⭐
            </Descriptions.Item>
            <Descriptions.Item label="Reputation Score">
              {user.reputationScore || 0}
            </Descriptions.Item>
            <Descriptions.Item label="Posts Created">{postCount}</Descriptions.Item>
            <Descriptions.Item label="Reviews Involved">{reviewCount}</Descriptions.Item>
          </Descriptions>
        </Card>

        {/* Interest Tags */}
        {user.interestTags && user.interestTags.length > 0 && (
          <Card title="Interest Tags">
            <Space wrap>
              {user.interestTags.map((tag) => (
                <Tag key={tag} color="blue">
                  {tag}
                </Tag>
              ))}
            </Space>
          </Card>
        )}
      </div>
    </MainLayout>
  )
}
