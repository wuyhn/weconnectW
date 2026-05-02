import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Card,
  Table,
  Button,
  Space,
  Input,
  Select,
  Row,
  Col,
  message,
  Popconfirm,
  Drawer,
  Descriptions,
  Tag,
  Empty,
} from 'antd'
import {
  SearchOutlined,
  DeleteOutlined,
  EyeOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import MainLayout from '../components/MainLayout'
import LoadingState from '../components/LoadingState'
import StatusBadge from '../components/StatusBadge'
import ConfirmActionModal from '../components/ConfirmActionModal'
import { postAdminService } from '../services/postAdminService'
import { userAdminService } from '../services/userAdminService'
import { Post, PaginationParams, User } from '../types'
import dayjs from 'dayjs'
import './PostsPage.css'

export default function PostsPage() {
  const [searchParams] = useSearchParams()
  const [posts, setPosts] = useState<Post[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<boolean | null>(null)
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [selectedPost, setSelectedPost] = useState<Post | null>(null)
  const [drawerVisible, setDrawerVisible] = useState(false)
  const [modalConfig, setModalConfig] = useState<any>(null)
  const [actionLoading, setActionLoading] = useState(false)
  const [userMap, setUserMap] = useState<Record<number, User>>({})  // Map authorId to User

  useEffect(() => {
    loadPosts(1)
  }, [search, statusFilter])

  useEffect(() => {
    // Load users for author name mapping
    loadUsers()
  }, [])

  useEffect(() => {
    // Handle query params from dashboard
    const fromDashboard = searchParams.get('from') === 'dashboard'
    const sortParam = searchParams.get('sort')
    if (fromDashboard) {
      // Show indication that we're coming from dashboard (optional)
      // Could set a banner or highlight
    }
    // Sort parameter can be used if needed: createdAt_desc
  }, [searchParams])

  const loadUsers = async () => {
    try {
      const result = await userAdminService.getUsers({ page: 1, pageSize: 1000 })
      const map: Record<number, User> = {}
      result.data.forEach((user) => {
        map[user.id] = user
      })
      setUserMap(map)
    } catch (error) {
      console.error('Failed to load users', error)
    }
  }

  const loadPosts = async (page: number) => {
    try {
      setLoading(true)
      const params: PaginationParams = { page, pageSize: 10 }
      const result = await postAdminService.getPosts(params, {
        search,
        archived: statusFilter,
      })
      setPosts(result.data)
      setPagination({ current: page, pageSize: 10, total: result.total })
    } catch (error) {
      message.error('Failed to load posts')
    } finally {
      setLoading(false)
    }
  }

  const handleDeletePost = async (postId: number) => {
    try {
      setActionLoading(true)
      await postAdminService.deletePost(postId)
      message.success('Post deleted successfully')
      loadPosts(pagination.current)
      setModalConfig(null)
    } catch (error: any) {
      message.error(error?.message || 'Failed to delete post')
    } finally {
      setActionLoading(false)
    }
  }

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 80,
      sorter: (a: Post, b: Post) => a.id - b.id,
    },
    {
      title: 'Author',
      dataIndex: 'authorId',
      key: 'authorId',
      width: 180,
      render: (authorId: number) => {
        const user = userMap[authorId]
        return user ? (
          <span>{user.fullName}</span>
        ) : (
          <span>User {authorId}</span>
        )
      },
      sorter: (a: Post, b: Post) => {
        const userA = userMap[a.authorId]
        const userB = userMap[b.authorId]
        return (userA?.fullName || '').localeCompare(userB?.fullName || '')
      },
    },
    {
      title: 'Content',
      dataIndex: 'content',
      key: 'content',
      width: 280,
      render: (text: string) => (
        <div
          style={{
            whiteSpace: 'normal',
            wordBreak: 'break-word',
            lineHeight: '1.5',
          }}
        >
          {text}
        </div>
      ),
    },
    {
      title: 'Image',
      dataIndex: 'imageUrl',
      key: 'imageUrl',
      width: 80,
      render: (url: string) => {
        if (!url) return null
        // Server-hosted images (new uploads)
        if (url.startsWith('/uploads/') || url.startsWith('http')) {
          return (
            <img
              src={url}
              alt="Post"
              style={{ width: 48, height: 48, objectFit: 'cover', borderRadius: 4 }}
              onError={(e) => { (e.target as HTMLImageElement).style.display = 'none' }}
            />
          )
        }
        // Old content:// URIs from Android — show indicator that image exists but can't be displayed
        return <Tag color="orange">📷 Local</Tag>
      },
    },
    {
      title: 'Interest Tag',
      dataIndex: 'interestTag',
      key: 'interestTag',
      width: 120,
      render: (tag: string) => <Tag>{tag}</Tag>,
    },
    {
      title: 'Location',
      dataIndex: 'location',
      key: 'location',
      width: 120,
    },
    {
      title: 'Members',
      dataIndex: 'maxMembers',
      key: 'maxMembers',
      width: 80,
      sorter: (a: Post, b: Post) => a.maxMembers - b.maxMembers,
    },
    {
      title: 'Start Time',
      dataIndex: 'startTime',
      key: 'startTime',
      width: 130,
      render: (date: string) => dayjs(date).format('MMM DD, HH:mm'),
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 130,
      render: (date: string) => dayjs(date).format('MMM DD, YYYY'),
      sorter: (a: Post, b: Post) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    },
    {
      title: 'Status',
      dataIndex: 'archived',
      key: 'status',
      width: 140,
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
      title: 'Actions',
      key: 'actions',
      width: 180,
      fixed: 'right' as const,
      render: (_: any, record: Post) => (
        <Space size="small">
          <Button
            type="primary"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => {
              setSelectedPost(record)
              setDrawerVisible(true)
            }}
          >
            View
          </Button>
          <Popconfirm
            title="Delete Post"
            description="Are you sure you want to delete this post? This action cannot be undone."
            onConfirm={() => handleDeletePost(record.id)}
            okText="Yes"
            cancelText="No"
            okButtonProps={{ danger: true }}
          >
            <Button danger size="small" icon={<DeleteOutlined />}>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  if (loading && posts.length === 0) {
    return <LoadingState fullPage message="Loading posts..." />
  }

  return (
    <MainLayout>
      <div className="posts-page">
        <Card className="posts-card">
          {/* Header */}
          <Row justify="space-between" align="middle" style={{ marginBottom: '24px' }}>
            <Col>
              <h2 style={{ margin: 0 }}>Post Management</h2>
            </Col>
            <Col>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => loadPosts(1)}
                loading={loading}
              >
                Refresh
              </Button>
            </Col>
          </Row>

          {/* Filters and Search */}
          <Row gutter={[16, 16]} style={{ marginBottom: '24px' }} className="filters-row">
            <Col xs={24} sm={24} md={12}>
              <Input
                placeholder="Search by content, tag, or location"
                prefix={<SearchOutlined />}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                allowClear
              />
            </Col>
            <Col xs={24} sm={24} md={6}>
              <Select
                style={{ width: '100%' }}
                placeholder="Lọc theo trạng thái"
                allowClear
                value={statusFilter}
                onChange={(value) => setStatusFilter(value ?? null)}
                options={[
                  { label: 'Đang hoạt động', value: false },
                  { label: 'Trong kho lưu trữ', value: true },
                ]}
              />
            </Col>
          </Row>

          {/* Table */}
          <Table
            columns={columns}
            dataSource={posts.map((p) => ({ ...p, key: p.id }))}
            loading={loading}
            pagination={{
              current: pagination.current,
              pageSize: pagination.pageSize,
              total: pagination.total,
              showSizeChanger: false,
              showTotal: (total) => `Total ${total} posts`,
              onChange: (page) => loadPosts(page),
            }}
            scroll={{ x: 1400 }}
            locale={{
              emptyText: (
                <Empty description="No posts found" style={{ marginTop: '48px' }} />
              ),
            }}
            defaultSortOrder="descend"
            defaultSortFieldName="createdAt"
          />
        </Card>

        {/* Post Detail Drawer */}
        <Drawer
          title="Post Details"
          placement="right"
          onClose={() => {
            setDrawerVisible(false)
            setSelectedPost(null)
          }}
          open={drawerVisible}
          width={500}
        >
          {selectedPost && (
            <div className="post-detail">
              <Descriptions bordered size="small" layout="vertical">
                <Descriptions.Item label="ID">{selectedPost.id}</Descriptions.Item>
                <Descriptions.Item label="Author ID">{selectedPost.authorId}</Descriptions.Item>
                <Descriptions.Item label="Content">{selectedPost.content}</Descriptions.Item>
                <Descriptions.Item label="Interest Tag">
                  <Tag>{selectedPost.interestTag}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="Location">{selectedPost.location}</Descriptions.Item>
                {selectedPost.imageUrl && (
                  <Descriptions.Item label="Image">
                    <img
                      src={selectedPost.imageUrl}
                      alt="Post"
                      style={{ maxWidth: '100%', height: 'auto', borderRadius: '4px' }}
                    />
                  </Descriptions.Item>
                )}
                <Descriptions.Item label="Max Members">{selectedPost.maxMembers}</Descriptions.Item>
                <Descriptions.Item label="Start Time">
                  {dayjs(selectedPost.startTime).format('MMMM DD, YYYY HH:mm')}
                </Descriptions.Item>
                <Descriptions.Item label="End Time">
                  {dayjs(selectedPost.endTime).format('MMMM DD, YYYY HH:mm')}
                </Descriptions.Item>
                <Descriptions.Item label="Status">
                  <StatusBadge
                    status={selectedPost.archived ? 'error' : 'success'}
                    text={selectedPost.archived ? 'Archived' : 'Active'}
                  />
                </Descriptions.Item>
                <Descriptions.Item label="Created">
                  {dayjs(selectedPost.createdAt).format('MMMM DD, YYYY')}
                </Descriptions.Item>
              </Descriptions>
            </div>
          )}
        </Drawer>

        {/* Confirm Action Modal */}
        {modalConfig && (
          <ConfirmActionModal
            title={modalConfig.title}
            message={modalConfig.message}
            onConfirm={modalConfig.onConfirm}
            onCancel={() => setModalConfig(null)}
            loading={actionLoading}
            type={modalConfig.title.includes('Delete') ? 'error' : 'warning'}
          />
        )}
      </div>
    </MainLayout>
  )
}
