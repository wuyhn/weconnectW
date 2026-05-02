import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Row, Col, Card, Table, Tabs, Spin, Space, Button, Empty, message } from 'antd'
import {
  UserOutlined,
  FileTextOutlined,
  StarOutlined,
  LockOutlined,
  FolderOutlined,
  EyeOutlined,
} from '@ant-design/icons'
import MainLayout from '../components/MainLayout'
import DashboardStatCard from '../components/DashboardStatCard'
import LoadingState from '../components/LoadingState'
import StatusBadge from '../components/StatusBadge'
import { dashboardService } from '../services/dashboardService'
import { userAdminService } from '../services/userAdminService'
import { postAdminService } from '../services/postAdminService'
import { reviewAdminService } from '../services/reviewAdminService'
import { DashboardStats, User, Post, UserReview } from '../types'
import dayjs from 'dayjs'
import './DashboardPage.css'

export default function DashboardPage() {
  const navigate = useNavigate()
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [users, setUsers] = useState<User[]>([])
  const [posts, setPosts] = useState<Post[]>([])
  const [reviews, setReviews] = useState<UserReview[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadData()
  }, [])

  const loadData = async () => {
    try {
      setLoading(true)
      const [statsData, usersData, postsData, reviewsData] = await Promise.all([
        dashboardService.getStats(),
        userAdminService.getRecentUsers(5),
        postAdminService.getRecentPosts(5),
        reviewAdminService.getRecentReviews(5),
      ])

      setStats(statsData)
      setUsers(usersData)
      setPosts(postsData)
      setReviews(reviewsData)
    } catch (error) {
      message.error('Failed to load dashboard data')
    } finally {
      setLoading(false)
    }
  }

  const handleViewAllUsers = () => {
    navigate('/users?from=dashboard&sort=createdAt_desc')
  }

  const handleViewAllPosts = () => {
    navigate('/posts?from=dashboard&sort=createdAt_desc')
  }

  const handleViewAllReviews = () => {
    navigate('/reviews?from=dashboard&sort=createdAt_desc')
  }

  if (loading) {
    return <LoadingState fullPage message="Loading dashboard..." />
  }

  // Users table columns
  const usersColumns = [
    {
      title: 'Name',
      dataIndex: 'fullName',
      key: 'fullName',
      render: (text: string, record: User) => (
        <Space>
          <span>{text}</span>
        </Space>
      ),
    },
    {
      title: 'Email',
      dataIndex: 'email',
      key: 'email',
      width: 200,
    },
    {
      title: 'Rating',
      dataIndex: 'averageRating',
      key: 'averageRating',
      render: (rating: number) => `${rating?.toFixed(1) || 0} ⭐`,
    },
    {
      title: 'Reputation',
      dataIndex: 'reputationScore',
      key: 'reputationScore',
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => dayjs(date).format('MMM DD, YYYY'),
      width: 120,
    },
  ]

  // Posts table columns
  const postsColumns = [
    {
      title: 'Content',
      dataIndex: 'content',
      key: 'content',
      render: (text: string) => (
        <div style={{ maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {text}
        </div>
      ),
    },
    {
      title: 'Interest Tag',
      dataIndex: 'interestTag',
      key: 'interestTag',
    },
    {
      title: 'Location',
      dataIndex: 'location',
      key: 'location',
    },
    {
      title: 'Members',
      dataIndex: 'maxMembers',
      key: 'maxMembers',
      width: 80,
    },
    {
      title: 'Status',
      dataIndex: 'archived',
      key: 'archived',
      width: 130,
      render: (_: any, record: Post) => {
        const isExpired = record.endTime
          ? new Date(record.endTime).getTime() < Date.now()
          : record.archived
        return (
          <StatusBadge
            status={isExpired ? 'error' : 'success'}
            text={isExpired ? 'Trong kho lưu trữ' : 'Đang hoạt động'}
          />
        )
      },
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => dayjs(date).format('MMM DD, YYYY'),
      width: 120,
    },
  ]

  // Reviews table columns
  const reviewsColumns = [
    {
      title: 'Activity',
      dataIndex: 'activityName',
      key: 'activityName',
    },
    {
      title: 'Label',
      dataIndex: 'reputationLabel',
      key: 'reputationLabel',
      render: (label: string) => {
        const colorMap: Record<string, string> = {
          Excellent: '#52c41a',
          Good: '#1890ff',
          Average: '#faad14',
          Poor: '#ff4d4f',
        }
        return (
          <span style={{ color: colorMap[label] || '#262626', fontWeight: 500 }}>
            {label}
          </span>
        )
      },
    },
    {
      title: 'Comment',
      dataIndex: 'comment',
      key: 'comment',
      render: (text: string) => (
        <div style={{ maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {text}
        </div>
      ),
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => dayjs(date).format('MMM DD, YYYY'),
      width: 120,
    },
  ]

  return (
    <MainLayout>
      <div className="dashboard-page">
        {/* Statistics Cards */}
        <Row gutter={[16, 16]} className="stats-row">
          <Col xs={24} sm={12} md={8} lg={4.8}>
            <DashboardStatCard
              title="Total Users"
              value={stats?.totalUsers || 0}
              icon={<UserOutlined />}
              color="blue"
              trendValue="+12% this month"
              trend="up"
            />
          </Col>
          <Col xs={24} sm={12} md={8} lg={4.8}>
            <DashboardStatCard
              title="Total Posts"
              value={stats?.totalPosts || 0}
              icon={<FileTextOutlined />}
              color="green"
              trendValue="+8% this week"
              trend="up"
            />
          </Col>
          <Col xs={24} sm={12} md={8} lg={4.8}>
            <DashboardStatCard
              title="Total Reviews"
              value={stats?.totalReviews || 0}
              icon={<StarOutlined />}
              color="orange"
              trendValue="+5 new reviews"
              trend="up"
            />
          </Col>
          <Col xs={24} sm={12} md={8} lg={4.8}>
            <DashboardStatCard
              title="Blocked Users"
              value={stats?.blockedUsers || 0}
              icon={<LockOutlined />}
              color="red"
              trendValue={`${stats?.blockedUsers || 0} accounts`}
            />
          </Col>
          <Col xs={24} sm={12} md={8} lg={4.8}>
            <DashboardStatCard
              title="Archived Posts"
              value={stats?.archivedPosts || 0}
              icon={<FolderOutlined />}
              color="purple"
              trendValue={`${stats?.archivedPosts || 0} inactive`}
            />
          </Col>
        </Row>

        {/* Recent Activity Tables */}
        <Row gutter={[16, 16]} style={{ marginTop: '32px' }}>
          <Col xs={24}>
            <Card
              title={
                <Space>
                  <UserOutlined />
                  <span>Recent Users</span>
                </Space>
              }
              extra={
                <Button type="primary" size="small" icon={<EyeOutlined />} onClick={handleViewAllUsers}>
                  View All
                </Button>
              }
              className="recent-card"
            >
              <Table
                columns={usersColumns}
                dataSource={users.map((u) => ({ ...u, key: u.id }))}
                pagination={false}
                size="small"
                locale={{ emptyText: <Empty description="No recent users" /> }}
              />
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]} style={{ marginTop: '16px' }}>
          <Col xs={24} md={12}>
            <Card
              title={
                <Space>
                  <FileTextOutlined />
                  <span>Recent Posts</span>
                </Space>
              }
              extra={
                <Button type="primary" size="small" icon={<EyeOutlined />} onClick={handleViewAllPosts}>
                  View All
                </Button>
              }
              className="recent-card"
            >
              <Table
                columns={postsColumns}
                dataSource={posts.map((p) => ({ ...p, key: p.id }))}
                pagination={false}
                size="small"
                locale={{ emptyText: <Empty description="No recent posts" /> }}
              />
            </Card>
          </Col>

          <Col xs={24} md={12}>
            <Card
              title={
                <Space>
                  <StarOutlined />
                  <span>Recent Reviews</span>
                </Space>
              }
              extra={
                <Button type="primary" size="small" icon={<EyeOutlined />} onClick={handleViewAllReviews}>
                  View All
                </Button>
              }
              className="recent-card"
            >
              <Table
                columns={reviewsColumns}
                dataSource={reviews.map((r) => ({ ...r, key: r.id }))}
                pagination={false}
                size="small"
                locale={{ emptyText: <Empty description="No recent reviews" /> }}
              />
            </Card>
          </Col>
        </Row>
      </div>
    </MainLayout>
  )
}
